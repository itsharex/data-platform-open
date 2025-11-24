package cn.dataplatform.open.flow.core.component.query;

import cn.dataplatform.open.common.source.JDBCSource;
import cn.dataplatform.open.common.source.Source;
import cn.dataplatform.open.common.source.SourceManager;
import cn.dataplatform.open.flow.core.Context;
import cn.dataplatform.open.flow.core.Flow;
import cn.dataplatform.open.flow.core.Transmit;
import cn.dataplatform.open.flow.core.component.FlowComponent;
import cn.dataplatform.open.flow.core.record.BatchPlainRecord;
import cn.dataplatform.open.flow.core.record.PlainRecord;
import cn.hutool.core.io.IoUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/7/13
 * @since 1.0.0
 */
@Slf4j
@Setter
@Getter
public abstract class JDBCQueryFlowComponent extends FlowComponent {

    private final SourceManager sourceManager;

    @NotBlank
    private String datasourceCode;
    @NotBlank
    private String selectText;

    /**
     * 单位秒
     */
    private Integer queryTimeout = 6;

    /**
     * 查询一次的数据量
     * </p>
     * 不指定则动态
     */
    private Long limit;

    /**
     * 自动方式-通过游标,每次拉取数量根据内存压力自动调整
     */
    private boolean streamQuery = true;

    /**
     * 滚动查询列
     */
    private String scrollColumn;

    public JDBCQueryFlowComponent(Flow flow, String code) {
        super(flow, code);
        this.sourceManager = this.getApplicationContext().getBean(SourceManager.class);
    }

    /**
     * 获取数据源
     *
     * @return 数据源
     */
    public JDBCSource getJDBCSource() {
        Source source = this.sourceManager.getSource(this.getWorkspaceCode(), this.getDatasourceCode());
        if (source == null) {
            throw new RuntimeException("数据源不存在:" + this.getDatasourceCode());
        }
        if (source instanceof JDBCSource jdbcSource) {
            return jdbcSource;
        } else {
            throw new RuntimeException("不支持的数据源类型:" + source.getClass());
        }
    }

    /**
     * 执行查询
     *
     * @param transmit 传输对象
     * @param context  上下文
     */
    @SneakyThrows
    @Override
    public void run(Transmit transmit, Context context) {
        log.info("开始执行查询:" + this.getCode() + ",查询语句:" + this.selectText);
        // 先根据内存压力获取每次拉取数量大小
        Long limit = this.limit;
        if (limit == null) {
            limit = this.autoLimit();
        }
        JDBCSource source = this.getJDBCSource();
        try (Connection connection = source.getConnection()) {
            if (this.streamQuery) {
                this.streamQuery(context, connection, limit);
            } else if (this.scrollColumn == null) {
                this.paginationQuery(context, connection, limit);
            } else {
                this.scrollQuery(context, connection, limit);
            }
        }
    }

    /**
     * 处理流式查询
     *
     * @param context    上下文
     * @param connection 数据库连接,流式查询会独占一个连接
     * @param limit      每次查询的数量
     * @throws SQLException SQL异常
     */
    public abstract void streamQuery(Context context, Connection connection, Long limit) throws Exception;


    /**
     * 处理分页查询
     *
     * @param context    上下文
     * @param connection 数据库连接
     * @param limit      每次查询的数量
     */
    public abstract void paginationQuery(Context context, Connection connection, Long limit) throws Exception;

    /**
     * 处理滚动查询
     *
     * @param context    上下文
     * @param connection 数据库连接
     * @param limit      每次查询的数量
     */
    public abstract void scrollQuery(Context context, Connection connection, Long limit) throws Exception;


    /**
     * 查询完成后的处理
     *
     * @param batchPlainRecord 查询结果
     * @param context          上下文
     */
    public void doNext(BatchPlainRecord batchPlainRecord, Context context) {
        if (batchPlainRecord.isEmpty()) {
            return;
        }
        log.info("开始传递数据到下一个节点");
        this.runNext(() -> {
            Transmit nextTransmit = new Transmit();
            nextTransmit.setFlowComponent(this);
            nextTransmit.setRecord(batchPlainRecord);
            return nextTransmit;
        }, context);
    }

    /**
     * 查询
     *
     * @param selectText 查询语句
     * @return 查询结果
     */
    @SuppressWarnings("all")
    @SneakyThrows
    public BatchPlainRecord query(Connection connection, String selectText) {
        log.info("开始查询:" + this.getKey() + ",查询语句:" + selectText);
        ResultSet resultSet = null;
        try (Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ) {
            statement.setQueryTimeout(this.getQueryTimeout());
            resultSet = statement.executeQuery(selectText);
            BatchPlainRecord records = new BatchPlainRecord();
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    record.put(columnName, value);
                }
                records.add(new PlainRecord(record));
            }
            log.info("查询完成:" + this.getCode() + ",查询到数据:" + records.size() + "条");
            return records;
        } finally {
            IoUtil.close(resultSet);
        }
    }

    /**
     * 自动调整查询条目
     *
     * @return 查询条目
     */
    public long autoLimit() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double memoryUsageRatio = (double) usedMemory / totalMemory * 100;
        log.info("当前内存使用情况:总内存:{}M,使用率:{}", totalMemory / 1024 / 1024, String.format("%.2f", memoryUsageRatio));
        long size;
        if (memoryUsageRatio > 90) {
            size = 1000;
        } else if (memoryUsageRatio > 80) {
            size = 2000;
        } else if (memoryUsageRatio > 70) {
            size = 5000;
        } else if (memoryUsageRatio > 60) {
            size = 10000;
        } else {
            size = 20000;
        }
        log.info("设置查询条目:{}", size);
        return size;
    }


}
