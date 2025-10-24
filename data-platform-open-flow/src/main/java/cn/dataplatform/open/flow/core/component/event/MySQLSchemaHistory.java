package cn.dataplatform.open.flow.core.component.event;

import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.flow.store.entity.DebeziumSchemaHistory;
import cn.dataplatform.open.flow.store.mapper.DebeziumSchemaHistoryMapper;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.debezium.annotation.ThreadSafe;
import io.debezium.config.Configuration;
import io.debezium.document.DocumentReader;
import io.debezium.document.DocumentWriter;
import io.debezium.relational.history.*;
import io.debezium.util.DelayStrategy;
import io.debezium.util.Loggings;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 在MySQL中存储模式历史的{@link SchemaHistory}实现。
 *
 * @author dingqianwen
 * @date 2025/6/8
 * @since 1.0.0
 */
@Slf4j
@ThreadSafe
public class MySQLSchemaHistory extends AbstractSchemaHistory {

    private final DocumentWriter writer = DocumentWriter.defaultWriter();
    private final DocumentReader reader = DocumentReader.defaultReader();

    private DebeziumSchemaHistoryMapper debeziumSchemaHistoryMapper;
    private String code;
    private String workspaceCode;
    private String flowCode;
    private ServerManager serverManager;
    private String requestId;

    /**
     * 配置
     *
     * @param config                 c
     * @param comparator             c
     * @param listener               l
     * @param useCatalogBeforeSchema u
     */
    @Override
    public void configure(Configuration config, HistoryRecordComparator comparator, SchemaHistoryListener listener, boolean useCatalogBeforeSchema) {
        this.debeziumSchemaHistoryMapper = SpringUtil.getBean(DebeziumSchemaHistoryMapper.class);
        this.serverManager = SpringUtil.getBean(ServerManager.class);
        String key = config.getString("schema.history.internal.key");
        String[] split = key.split("-");
        this.workspaceCode = split[0];
        this.flowCode = split[1];
        this.code = split[2];
        super.configure(config, comparator, listener, useCatalogBeforeSchema);
        this.requestId = MDC.get(Constant.REQUEST_ID);
    }

    /**
     * 开始
     */
    @Override
    public synchronized void start() {
        super.start();
        log.info("启动 MySQLSchemaHistory");
        this.doWithRetry(() -> true, "连接到MySQL");
    }


    /**
     * 存储记录到 MySQL 中
     *
     * @param record ddl记录
     * @throws SchemaHistoryException s
     */
    @Override
    protected void storeRecord(HistoryRecord record) throws SchemaHistoryException {
        if (record == null) {
            return;
        }
        try {
            MDC.put(Constant.FLOW_CODE, this.flowCode);
            MDC.put(Constant.REQUEST_ID, this.requestId);
            String line;
            try {
                line = this.writer.write(record.document());
            } catch (IOException e) {
                Loggings.logErrorAndTraceRecord(log, record, "未能将记录转换为字符串", e);
                throw new SchemaHistoryException("无法写入数据库架构历史记录");
            }
            this.doWithRetry(() -> {
                DebeziumSchemaHistory debeziumSchemaHistory = new DebeziumSchemaHistory();
                debeziumSchemaHistory.setWorkspaceCode(this.workspaceCode);
                debeziumSchemaHistory.setFlowCode(this.flowCode);
                debeziumSchemaHistory.setComponentCode(this.code);
                debeziumSchemaHistory.setInstanceId(this.serverManager.instanceId());
                debeziumSchemaHistory.setSchemaLine(line);
                debeziumSchemaHistory.setCreateTime(LocalDateTime.now());
                debeziumSchemaHistory.setExpireTime(LocalDateTime.now().plusDays(100));
                this.debeziumSchemaHistoryMapper.insert(debeziumSchemaHistory);
                log.info("MySQL中写入数据库模式历史的记录:" + line);
                return true;
            }, "写入数据库模式历史流");
        } finally {
            MDC.clear();
        }
    }

    /**
     * 停止
     */
    @Override
    public void stop() {
        super.stop();
    }

    /**
     * 恢复记录
     *
     * @param records r
     */
    @Override
    protected synchronized void recoverRecords(Consumer<HistoryRecord> records) {
        MDC.put(Constant.FLOW_CODE, this.flowCode);
        MDC.put(Constant.REQUEST_ID, this.requestId);
        try {
            final List<DebeziumSchemaHistory> entries = doWithRetry(() -> this.debeziumSchemaHistoryMapper.selectList(Wrappers.
                            <DebeziumSchemaHistory>lambdaQuery()
                            .eq(DebeziumSchemaHistory::getWorkspaceCode, this.workspaceCode)
                            .eq(DebeziumSchemaHistory::getFlowCode, this.flowCode)
                            .eq(DebeziumSchemaHistory::getComponentCode, this.code)
                            .orderByAsc(DebeziumSchemaHistory::getId)
                    ),
                    "写入数据库模式历史流");
            for (DebeziumSchemaHistory entry : entries) {
                try {
                    records.accept(new HistoryRecord(this.reader.read(entry.getSchemaLine())));
                } catch (IOException e) {
                    log.error("未能将记录转换为字符串:" + entry, e);
                    return;
                }
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * 记录是否存在
     *
     * @return 当前记录数
     */
    @Override
    public boolean storageExists() {
        return true;
    }

    /**
     * 获取当前记录数
     *
     * @return 当前记录数
     */
    @Override
    public boolean exists() {
        return this.doWithRetry(() -> this.debeziumSchemaHistoryMapper.exists(Wrappers.<DebeziumSchemaHistory>lambdaQuery()
                .eq(DebeziumSchemaHistory::getWorkspaceCode, this.workspaceCode)
                .eq(DebeziumSchemaHistory::getFlowCode, this.flowCode)
                .eq(DebeziumSchemaHistory::getComponentCode, this.code)
        ), "检查是否存在以前的记录");
    }

    /**
     * 执行重试逻辑
     *
     * @param action      a
     * @param description d
     * @param <T>         t
     * @return r
     */
    private <T> T doWithRetry(Supplier<T> action, String description) {
        final var delayStrategy = DelayStrategy.exponential(Duration.ofMillis(300), Duration.ofMillis(10000L));
        for (int i = 1; i <= 10; i++) {
            try {
                return action.get();
            } catch (Exception e) {
                log.warn(description + "失败，将重试", e);
            }
            delayStrategy.sleepWhen(true);
        }
        throw new SchemaHistoryException(String.format("经过%d次尝试后，无法连接到MySQL。", 10));
    }

}
