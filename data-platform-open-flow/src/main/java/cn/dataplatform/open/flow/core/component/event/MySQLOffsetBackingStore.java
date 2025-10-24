package cn.dataplatform.open.flow.core.component.event;

import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.flow.store.entity.DebeziumSavePoint;
import cn.dataplatform.open.flow.store.mapper.DebeziumSavePointMapper;
import cn.hutool.core.lang.UUID;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.debezium.annotation.VisibleForTesting;
import io.debezium.config.Configuration;
import io.smallrye.mutiny.Uni;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.MemoryOffsetBackingStore;
import org.slf4j.MDC;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 基于MySQL的偏移后备存储的实现
 *
 * @author dingqianwen
 * @date 2025/6/8
 * @since 1.0.0
 */
@Slf4j
public class MySQLOffsetBackingStore extends MemoryOffsetBackingStore {

    private String code;
    private String workspaceCode;
    private String flowCode;
    /**
     * 保留时间 单位小时
     */
    private Integer savePointDuration = 30;
    private String savePoint;
    private String startStrategy;
    private DebeziumSavePointMapper debeziumSavePointMapper;
    private ServerManager serverManager;
    private String requestId;

    /**
     * 配置
     *
     * @param config can be DistributedConfig or StandaloneConfig
     */
    @Override
    public void configure(WorkerConfig config) {
        super.configure(config);
        Configuration configuration = Configuration.from(config.originalsStrings());
        this.savePoint = configuration.getString("offset.storage.save.point");
        this.savePointDuration = configuration.getInteger("offset.storage.save.point.duration", 30);
        this.debeziumSavePointMapper = SpringUtil.getBean(DebeziumSavePointMapper.class);
        this.code = configuration.getString("task.code");
        this.flowCode = configuration.getString("flow.code");
        this.workspaceCode = configuration.getString("workspace.code");
        this.startStrategy = configuration.getString("start.strategy");
        this.requestId = configuration.getString("requestId");
        this.serverManager = SpringUtil.getBean(ServerManager.class);
    }

    /**
     * 开始
     */
    @Override
    public synchronized void start() {
        super.start();
        log.info("正在启动 MySQLOffsetBackingStore");
        this.load();
    }


    /**
     * 关闭
     */
    @Override
    public synchronized void stop() {
        super.stop();
        log.info("关闭 MySQLOffsetBackingStore");
    }


    /**
     * 从MySQL加载偏移量。
     */
    @VisibleForTesting
    void load() {
        MDC.put(Constant.FLOW_CODE, this.flowCode);
        MDC.put(Constant.REQUEST_ID, this.requestId);
        try {
            DebeziumSavePoint point;
            // 优先级最高
            if (this.savePoint != null) {
                point = this.debeziumSavePointMapper.selectOne(Wrappers.<DebeziumSavePoint>lambdaQuery()
                        .eq(DebeziumSavePoint::getWorkspaceCode, this.workspaceCode)
                        .eq(DebeziumSavePoint::getFlowCode, this.flowCode)
                        .eq(DebeziumSavePoint::getComponentCode, this.code)
                        .eq(DebeziumSavePoint::getSavePoint, this.savePoint)
                );
                if (point == null) {
                    throw new RuntimeException("保存点未找到:" + this.savePoint);
                }
            } else {
                if (this.startStrategy.equals(StartStrategy.STOCK_AND_INCREASE.name())) {
                    // 每次按照存量以及增量启动,无需加载保存点
                    return;
                }
                point = this.debeziumSavePointMapper.selectOne(Wrappers.<DebeziumSavePoint>lambdaQuery()
                        .eq(DebeziumSavePoint::getWorkspaceCode, this.workspaceCode)
                        .eq(DebeziumSavePoint::getFlowCode, this.flowCode)
                        .eq(DebeziumSavePoint::getComponentCode, this.code)
                        // 获取最新的1条记录
                        .orderByDesc(DebeziumSavePoint::getId)
                        .last(Constant.LIMIT_ONE)
                );
                if (point == null) {
                    if (this.startStrategy.equals(StartStrategy.LAST_STORAGE.name())) {
                        // 从最新的一次保存点启动,如果没有,则报错
                        throw new RuntimeException("没有找到最新保存点");
                    }
                    // 按照无保存点启动
                    return;
                }
            }
            log.info("从MySQL加载偏移量:" + point);
            this.data = new HashMap<>();
            ByteBuffer key = (point.getKey() != null) ? ByteBuffer.wrap(point.getKey().getBytes()) : null;
            ByteBuffer value = (point.getValue() != null) ? ByteBuffer.wrap(point.getValue().getBytes()) : null;
            this.data.put(key, value);
        } finally {
            MDC.clear();
        }
    }


    /**
     * 将偏移量保存到MySQL。
     */
    @Override
    protected void save() {
        MDC.put(Constant.FLOW_CODE, this.flowCode);
        MDC.put(Constant.REQUEST_ID, this.requestId);
        try {
            for (Map.Entry<ByteBuffer, ByteBuffer> mapEntry : this.data.entrySet()) {
                if (mapEntry.getKey() == null || mapEntry.getValue() == null) {
                    log.warn("跳过偏移量中的空键或值");
                    continue;
                }
                byte[] key = mapEntry.getKey().array();
                byte[] value = mapEntry.getValue().array();
                DebeziumSavePoint point = new DebeziumSavePoint()
                        .setWorkspaceCode(this.workspaceCode)
                        .setFlowCode(this.flowCode)
                        .setComponentCode(this.code)
                        .setSavePoint(UUID.randomUUID().toString(true))
                        .setInstanceId(this.serverManager.instanceId())
                        .setKey(new String(key))
                        .setValue(new String(value))
                        .setCreateTime(LocalDateTime.now())
                        .setExpireTime(LocalDateTime.now().plusDays(this.savePointDuration));
                Uni.createFrom().item(() -> {
                            // key ["mysql-connector",{"server":"my-app-connector-"}]
                            return this.debeziumSavePointMapper.insert(point);
                        })
                        .onFailure().invoke(
                                f -> {
                                    log.warn("写入MySQL偏移存储失败" + f);
                                    log.warn("将重试");
                                })
                        .onFailure().retry().withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(2)).indefinitely()
                        .invoke(
                                item -> {
                                    log.info("写入MySQL的偏移量:" + point);
                                })
                        .await().indefinitely();
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * 连接器的分区
     *
     * @param connectorName 连接器名称
     * @return 分区集合
     */
    @Override
    public Set<Map<String, Object>> connectorPartitions(String connectorName) {
        return null;
    }

}
