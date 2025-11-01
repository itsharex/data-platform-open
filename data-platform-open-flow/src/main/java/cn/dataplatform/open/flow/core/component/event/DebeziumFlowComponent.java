package cn.dataplatform.open.flow.core.component.event;

import cn.dataplatform.open.common.body.DataFlowMessageBody;
import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.enums.RedisKey;
import cn.dataplatform.open.common.enums.ServerStatus;
import cn.dataplatform.open.common.event.DataFlowEvent;
import cn.dataplatform.open.common.event.EventPublisher;
import cn.dataplatform.open.common.server.Server;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.common.source.*;
import cn.dataplatform.open.common.util.tuple.Tuple2;
import cn.dataplatform.open.common.vo.flow.FlowError;
import cn.dataplatform.open.flow.core.Context;
import cn.dataplatform.open.flow.core.Flow;
import cn.dataplatform.open.flow.core.Transmit;
import cn.dataplatform.open.flow.core.annotation.ExcludeMonitor;
import cn.dataplatform.open.flow.core.component.FlowComponent;
import cn.dataplatform.open.flow.core.component.event.connector.*;
import cn.dataplatform.open.flow.core.component.event.convert.BinaryConverter;
import cn.dataplatform.open.flow.core.component.event.convert.DateTimeConverter;
import cn.dataplatform.open.flow.core.component.event.convert.MongoDataConverter;
import cn.dataplatform.open.flow.core.exception.FlowRunNextException;
import cn.dataplatform.open.flow.core.monitor.FlowComponentMonitor;
import cn.dataplatform.open.flow.core.monitor.FlowMonitor;
import cn.dataplatform.open.flow.core.record.BatchStreamRecord;
import cn.dataplatform.open.flow.core.record.StreamRecord;
import cn.dataplatform.open.common.enums.Status;
import cn.dataplatform.open.flow.store.mapper.DebeziumSavePointMapper;
import cn.dataplatform.open.flow.vo.data.flow.FlowComponentOnly;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import io.debezium.config.Configuration;
import io.debezium.data.Envelope;
import io.debezium.embedded.Connect;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.KeyValueHeaderChangeEventFormat;
import io.micrometer.core.instrument.Timer;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.redisson.api.*;
import org.slf4j.MDC;

import jakarta.validation.constraints.NotBlank;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static io.debezium.data.Envelope.FieldName.*;
import static java.util.stream.Collectors.toMap;

/**
 * 〈 <a href="https://debezium.io/documentation/reference/3.0/connectors/mysql.html">debezium documentation</a>〉
 *
 * @author dingqianwen
 * @date 2025/1/7
 * @since 1.0.0
 */
@ExcludeMonitor
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class DebeziumFlowComponent extends FlowComponent {

    /**
     * DebeziumFlow 专用线程池
     */
    private ExecutorService execute = Executors.newSingleThreadExecutor();
    private Future<?> executeFuture;

    private RedissonClient redissonClient;
    private SourceManager sourceManager;
    private ServerManager serverManager;
    private FlowComponentMonitor flowComponentMonitor;

    @NotBlank
    private String datasourceCode;
    /**
     * 逗号隔开
     */
    @NotBlank
    private String schemas;
    /**
     * 逗号隔开
     * <p>
     * schema.table
     */
    private String tables;
    /**
     * 状态
     */
    private Status status = Status.ENABLE;
    /**
     * 自定义配置信息
     */
    private Properties properties;

    /**
     * 监听的操作类型
     */
    private List<Envelope.Operation> operations = Arrays.asList(Envelope.Operation.READ,
            Envelope.Operation.CREATE,
            Envelope.Operation.UPDATE,
            Envelope.Operation.DELETE);

    private DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> debeziumEngine;

    /**
     * 启动策略
     */
    private StartStrategy startStrategy = StartStrategy.AUTO;
    /**
     * 保存间隔(毫秒)
     */
    private Integer savePointInterval = 5000;
    /**
     * 保留时长(天)
     */
    private Integer savePointDuration = 30;
    /**
     * 保存点唯一编码,优先级高于startStrategy
     */
    private String savePoint;

    /**
     * 如果没有指定,则自己随机生成一个,从5400-6400中随机分配一个数字
     */
    private Integer databaseServerId;
    /**
     * oracle listener server name
     * SELECT name FROM v$services;
     * XE or ORCLCDB ...
     */
    private String listenerServerName;

    /**
     * max.batch.size
     */
    private Integer maxBatchSize = 2048;

    /**
     * GMT/UTC
     */
    private String connectionTimeZone = "GMT+08";

    /**
     * 线程数
     */
    private Integer threadNumber;

    private DebeziumSavePointMapper debeziumSavePointMapper;
    private FlowMonitor flowMonitor;

    public DebeziumFlowComponent(Flow flow, String code) {
        super(flow, code);
        this.redissonClient = SpringUtil.getBean(RedissonClient.class);
        this.debeziumSavePointMapper = SpringUtil.getBean(DebeziumSavePointMapper.class);
        this.sourceManager = SpringUtil.getBean(SourceManager.class);
        this.serverManager = SpringUtil.getBean(ServerManager.class);
        this.flowComponentMonitor = SpringUtil.getBean(FlowComponentMonitor.class);
        this.flowMonitor = this.getApplicationContext().getBean(FlowMonitor.class);
    }

    /**
     * 执行
     *
     * @param transmit 传输
     * @param context  上下文
     */
    @SneakyThrows
    @Override
    public void run(@Nullable Transmit transmit, Context context) {
        Source source = this.sourceManager.getSource(this.getWorkspaceCode(), this.datasourceCode);
        if (source == null) {
            throw new RuntimeException("数据源不存在");
        }
        // 必须唯一,否则 JmxUtils  Unable to unregister metrics MBean ...
        String topic = "dp-flow-topic-" + this.getCode();
        String name = "dp-flow-connector-" + this.getCode();
        // 放在前置设置，空属性等异常即时提示出去，不用单独线程内部执行
        // io.debezium.config.CommonConnectorConfig
        Configuration.Builder builder = Configuration.create()
                .with("workspace.code", this.getWorkspaceCode())
                .with("flow.code", this.getFlowCode())
                .with("task.code", this.getCode())
                .with("requestId", context.getId())
                // to MySQLSchemaHistory
                .with("schema.history.internal.key", this.getKey())
                // 启动保存点策略
                .with("start.strategy", this.startStrategy.name())
                .with("topic.prefix", topic)
                // 偏移量持久化,用来容错
                .with("offset.storage", MySQLOffsetBackingStore.class)
                // 捕获偏移量的周期
                .with("offset.flush.interval.ms", this.savePointInterval)
                // 保留时间
                .with("offset.storage.save.point.duration", this.savePointDuration)
                // 设置延迟间隔有助于防止在快照完成后、流式传输进程开始之前发生故障时连接器重新启动快照。
                .with("streaming.delay.ms", this.savePointInterval + 1000)
                // 连接器的唯一名称
                .with("name", name)
                .with("table.include.list", this.tables)
                .with("database.connectionTimeZone", this.connectionTimeZone)
                .with("max.batch.size", this.maxBatchSize)
                // 配置阻塞队列
                // Connector configuration is not valid. The 'max.queue.size' value is invalid: Must be larger than the maximum batch size
                // 始终保持 1 比 4，页面最多配置2w，队列最多8w
                .with("max.queue.size", this.maxBatchSize * 4)
                .with("converters", "dateConverters,binaryConverters")
                .with("dateConverters.type", DateTimeConverter.class)
                .with("dateConverters.timezone", this.connectionTimeZone)
                .with("binaryConverters.type", BinaryConverter.class)
                .with("decimal.handling.mode", "double")
                //.with("binary.handling.mode", "hex")
                // 是否包含schema变更
                .with("include.schema.changes", false)
                // MySQL服务器或集群的逻辑名称
                .with("schema.history.internal.name", "dp-server-" + this.getCode())
                //.with("database.server.name", "dp-db-server-" + this.getCode())
                // 历史变更记录
                .with("schema.history.internal", MySQLSchemaHistory.class.getName())
                // 只会保存与那些被 Debezium 明确配置要捕获变更的数据库相关的 DDL 信息,而忽略其他数据库的 DDL 信息。
                .with("schema.history.internal.store.only.captured.databases.ddl", "true")
                // 只会存储那些明确被配置为要捕获变更的表的 DDL（数据定义语言,例如 CREATE TABLE、ALTER TABLE 等操作）语句。
                // 而对于其他未被配置捕获的表的 DDL 语句,不会被记录到 Schema 历史中。
                .with("schema.history.internal.store.only.captured.tables.ddl", "true")
                // 使用无锁模式
                .with("snapshot.locking.mode", "none")
                // snapshot.max.threads
                .with("snapshot.max.threads", this.getThreads())
                // https://issues.redhat.com/browse/DBZ-7342
                .with("errors.max.retries", 3)
                // 如果没有数据流动，多久发送一次心跳
                // 解决自动模式存在问题  当存量同步完，过程或者以后没有任何变更时，没有后续保存点了，重启后将会再次触发存量
                .with("heartbeat.interval.ms", 10 * 1000)
                // 指定连接器在binlog事件反序列化期间应如何对异常做出反应。
                // warn记录有问题的事件及其binlog偏移量，然后跳过该事件。
                .with("event.processing.failure.handling.mode", "warn")
                // 指定连接器应如何响应与内部架构表示中不存在的表相关的 binlog 事件。也就是说，内部表示与数据库不一致。
                // warn记录有问题的事件及其 binlog 偏移量并跳过该事件。
                .with("inconsistent.schema.handling.mode", "warn")
                // .with("signal.enabled.channels", "file")
                ;
        // 配置连接器
        ConnectorConfigure connectorConfigure = ConnectorConfigure.get(source);
        connectorConfigure.configure(this, source, builder);
        if (StartStrategy.CUSTOM.equals(this.startStrategy)) {
            if (this.savePoint != null) {
                // 查询是否存在
                builder.with("offset.storage.save.point", this.savePoint);
            } else {
                throw new RuntimeException("未设置保存点");
            }
        }
        if (this.startStrategy.equals(StartStrategy.INCREASE)) {
            // 不会在启动时生成存量数据的快照
            builder.with("snapshot.mode", "never");
        } else if (this.startStrategy.equals(StartStrategy.RECOVERY)) {
            builder.with("snapshot.mode", "recovery");
        }
        // 自定义SQL
        // snapshot.select.statement.overrides=inventory.products,inventory.customers
        // snapshot.select.statement.overrides.inventory.products=SELECT * FROM inventory.products WHERE category='electronics'
        // snapshot.select.statement.overrides.inventory.customers=SELECT id, name, email FROM inventory.customers WHERE status='active'
        // 自定义配置覆盖默认配置
        if (CollUtil.isNotEmpty(this.properties)) {
            this.properties.forEach((k, v) -> builder.with(k.toString(), v.toString()));
        }
        if (this.execute == null || this.execute.isShutdown()) {
            this.execute = Executors.newSingleThreadExecutor();
        }
        this.executeFuture = this.execute.submit(() -> {
            MDC.put(Constant.FLOW_CODE, this.getFlowCode());
            MDC.put(Constant.REQUEST_ID, context.getId());
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            RLock lock = this.redissonClient.getLock(RedisKey.FLOW_DEBEZIUM_LOCK.build(this.getKey()));
            try {
                if (!lock.tryLock()) {
                    log.info("DebeziumFlowComponent:{} 已经被其他实例抢占并执行,当前实例:{}",
                            this.getCode(), this.serverManager.instanceId());
                    // 未获取到锁的实例跳过
                    return;
                } else {
                    log.info("DebeziumEngine获取锁:{},实例:{}", this.getKey(), this.serverManager.instanceId());
                    // 判断是否是否允许当前实例执行
                    if (!this.canAssumeExecutionDuty()) {
                        return;
                    }
                }
                // 标记被当前节点执行
                RBucket<FlowComponentOnly> flowComponentOnlyRBucket = this.redissonClient.getBucket(RedisKey.FLOW_COMPONENT_ONLY.build(this.getKey()));
                FlowComponentOnly flowComponentOnly = new FlowComponentOnly();
                flowComponentOnly.setInstanceId(this.serverManager.instanceId());
                flowComponentOnly.setStartTime(LocalDateTime.now());
                flowComponentOnlyRBucket.set(flowComponentOnly);
                // 开始启动
                Configuration configuration = builder.build();
                this.debeziumEngine = DebeziumEngine.create(
                                // ChangeEventFormat.of(Connect.class)
                                // 修改为AsyncEmbeddedEngine
                                KeyValueHeaderChangeEventFormat.of(Connect.class, Connect.class, Connect.class),
                                "io.debezium.embedded.async.ConvertingAsyncEngineBuilderFactory"
                        )
                        .using(configuration.asProperties())
                        .using((success, message, throwable) -> {
                            Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
                            if (CollUtil.isEmpty(copyOfContextMap)) {
                                MDC.setContextMap(contextMap);
                            }
                            if (!success) {
                                log.error("DebeziumEngine执行异常中断:" + message, throwable);
                                // bug修复
                                if (throwable == null) {
                                    throwable = new Exception(message);
                                }
                                // 异常处理
                                this.exception(throwable, FlowError.ErrorType.ABORT);
                            }
                        })
                        .using(new DebeziumEngine.ConnectorCallback() {

                            @Override
                            public void connectorStarted() {
                                Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
                                if (CollUtil.isEmpty(copyOfContextMap)) {
                                    MDC.setContextMap(contextMap);
                                }
                            }

                            @Override
                            public void taskStarted() {
                                Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
                                if (CollUtil.isEmpty(copyOfContextMap)) {
                                    MDC.setContextMap(contextMap);
                                }
                            }

                            @Override
                            public void connectorStopped() {
                                Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
                                if (CollUtil.isEmpty(copyOfContextMap)) {
                                    MDC.setContextMap(contextMap);
                                }
                                log.warn("DebeziumEngine连接器已停止");
                            }

                            @Override
                            public void taskStopped() {
                                // 修复
                                Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
                                if (CollUtil.isEmpty(copyOfContextMap)) {
                                    MDC.setContextMap(contextMap);
                                }
                                log.warn("DebeziumEngine任务已停止");
                            }
                        })
                        .notifying((recordChangeEvents, recordCommitter) -> {
                            // 修复
                            MDC.setContextMap(contextMap);
                            try {
                                this.handlePayload(recordChangeEvents, recordCommitter, context);
                            } finally {
                                MDC.clear();
                            }
                        })
                        .build();
                log.info("DebeziumEngine开始启动");
                // 内部停止时会让当前线程中断
                this.debeziumEngine.run();
                log.info("DebeziumEngine执行结束");
            } catch (Exception e) {
                log.error("DebeziumEngine执行失败", e);
                this.exception(e, FlowError.ErrorType.STARTUP);
            } finally {
                // 释放锁
                try {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        log.info("DebeziumEngine释放锁:{}", this.getKey());
                        lock.unlock();
                    }
                } catch (Exception e) {
                    // 释放锁，或者移除心跳数据异常
                    log.warn("DebeziumEngine释放锁或移除心跳数据异常:" + this.getKey(), e);
                }
                MDC.clear();
            }
        });
    }


    /**
     * 判断是否是否允许当前实例执行
     *
     * @return 返回true时，允许当前实例执行，返回false时，表示其他节点正在执行，当前节点不需要执行
     */
    private boolean canAssumeExecutionDuty() {
        RBucket<FlowComponentOnly> flowComponentOnlyRBucket = this.redissonClient.getBucket(RedisKey.FLOW_COMPONENT_ONLY.build(this.getKey()));
        FlowComponentOnly flowComponentOnly = flowComponentOnlyRBucket.get();
        // 是否健康的
        if (flowComponentOnly != null
                // 如果遇到异常等重启时，正巧当前实例是运行中的实例，不能被直接return出去，直接还是当前节点执行
                && !Objects.equals(flowComponentOnly.getInstanceId(), this.serverManager.instanceId())) {
            // 查看运行中实例是否还存活，如果存活则直接跳过
            Server server = this.serverManager.get(flowComponentOnly.getInstanceId());
            if (server != null) {
                // 如果是在线
                if (Objects.equals(server.getStatus(), ServerStatus.ONLINE)) {
                    log.info("DebeziumFlowComponent:{} 正在运行中,忽略本次启动,当前实例:{},运行中实例:{}", this.getKey(),
                            this.serverManager.instanceId(), flowComponentOnly.getInstanceId());
                    // 已在线本次不启动
                    return false;
                }
                // 如果是失活
                if (Objects.equals(server.getStatus(), ServerStatus.INACTIVE)) {
                    // 如果已经长达3分钟（因为防止是因为redis网络等问题导致假死）没有在线，也认为宕机了
                    if (server.getLastHeartbeat() != null && LocalDateTime.now().isAfter(server.getLastHeartbeat().plusMinutes(3))) {
                        log.info("DebeziumFlowComponent:{} 运行中实例:{} 已经超过3分钟没有心跳,认为已经宕机,开始重新启动,当前实例:{}", this.getKey(),
                                flowComponentOnly.getInstanceId(), this.serverManager.instanceId());
                        // 执行后续启动逻辑
                    } else {
                        log.info("DebeziumFlowComponent:{} 运行中实例:{} 状态失活,忽略本次启动,最多等待3分钟后舍弃此实例,当前实例:{}", this.getKey(),
                                flowComponentOnly.getInstanceId(), this.serverManager.instanceId());
                        // 本次不启动
                        return false;
                    }
                }
                // 如果是离线
                if (Objects.equals(server.getStatus(), ServerStatus.OFFLINE)) {
                    log.info("DebeziumFlowComponent:{} 运行中实例:{} 状态离线,开始重新启动,当前实例:{}", this.getKey(),
                            flowComponentOnly.getInstanceId(), this.serverManager.instanceId());
                    // 执行后续启动逻辑
                }
            } else {
                log.info("DebeziumFlowComponent:{} 运行中实例:{} 不存在,开始重新启动,当前实例:{}", this.getKey(),
                        flowComponentOnly.getInstanceId(), this.serverManager.instanceId());
            }
        } else {
            log.info("DebeziumFlowComponent:{} 开始执行,实例:{}", this.getKey(), this.serverManager.instanceId());
        }
        // 允许当前节点执行
        return true;
    }


    /**
     * 告警，并中断数据流
     *
     * @param e         异常
     * @param errorType 异常类型
     */
    private void exception(Throwable e, FlowError.ErrorType errorType) {
        Throwable rootCause = ExceptionUtil.getRootCause(e);
        // 停止数据流
        DataFlowMessageBody dataFlowMessageBody = new DataFlowMessageBody();
        dataFlowMessageBody.setType(DataFlowMessageBody.Type.REMOVE);
        dataFlowMessageBody.setId(this.getFlow().getId());
        dataFlowMessageBody.setWorkspaceCode(this.getWorkspaceCode());
        EventPublisher.publishEvent(new DataFlowEvent(dataFlowMessageBody));
        // 标记异常中断
        this.flowMonitor.errorWithAlarm(this, rootCause, errorType);
    }

    /**
     * 处理变更事件
     *
     * @param recordChangeEvents 变更事件
     * @param recordCommitter    提交者
     */
    private void handlePayload(List<ChangeEvent<SourceRecord, SourceRecord>> recordChangeEvents,
                               DebeziumEngine.RecordCommitter<ChangeEvent<SourceRecord, SourceRecord>> recordCommitter,
                               Context context) throws InterruptedException {
        if (!this.getFlow().isRunning()) {
            // throw new RuntimeException("数据流已停止,不再处理变更事件");
            // 修复因二次RuntimeException报错，覆盖原有的异常
            log.info("组件已经停止,不再处理变更事件");
            return;
        }
        BatchStreamRecord changeRecord = new BatchStreamRecord();
        recordChangeEvents.forEach(r -> {
            SourceRecord sourceRecord = r.value();
            String topic = sourceRecord.topic();
            Struct sourceRecordChangeValue = (Struct) sourceRecord.value();
            if (sourceRecordChangeValue != null) {
                // 判断操作的类型 过滤掉读 只处理增删改   这个其实可以在配置中设置
                Envelope.Operation operation = null;
                Field operationField = sourceRecordChangeValue.schema().field(OPERATION);
                // 优先看是否存在操作事件
                if (operationField == null) {
                    // 心跳事件
                    Field tsMs = sourceRecordChangeValue.schema().field("ts_ms");
                    if (tsMs != null) {
                        // 获取时间戳
                        Long tsMsValue = (Long) sourceRecordChangeValue.get(tsMs);
                        if (this.isDebug()) {
                            log.debug("DebeziumFlowComponent:{},心跳事件,当前时间戳:{}", this.getKey(), tsMsValue);
                        }
                        return;
                    }
                } else {
                    operation = Envelope.Operation.forCode((String) sourceRecordChangeValue.get(operationField));
                }
                // 如果没有这个key报错,直接跳过,有可能是表结构变化
                if (operation == null) {
                    log.warn("获取操作类型失败-跳过处理:" + sourceRecordChangeValue);
                    return;
                }
                if (this.operations.contains(operation)) {
                    StreamRecord streamRecord = new StreamRecord();
                    // 获取增删改对应的结构体数据
                    Object beforeObject = sourceRecordChangeValue.get(BEFORE);
                    if (beforeObject instanceof Struct struct) {
                        List<Field> fields = struct.schema().fields();
                        // 将变更的行封装为Map
                        Map<String, Object> payload = this.getStringObjectMap(fields, struct);
                        streamRecord.setBefore(payload);
                    } else if (beforeObject instanceof String string) {
                        // mongoDB
                        streamRecord.setBefore(MongoDataConverter.convert(string, this.connectionTimeZone));
                    }
                    Object afterObject = sourceRecordChangeValue.get(AFTER);
                    if (afterObject instanceof Struct struct) {
                        List<Field> fields = struct.schema().fields();
                        // 将变更的行封装为Map
                        Map<String, Object> payload = this.getStringObjectMap(fields, struct);
                        streamRecord.setAfter(payload);
                    } else if (afterObject instanceof String string) {
                        // mongoDB
                        streamRecord.setAfter(MongoDataConverter.convert(string, this.connectionTimeZone));
                    }
                    // 如果before after都没有数据时
                    // 例如mongodb默认没有开启changeStreamPreAndPostImages，则before没有数据，删除时都是空的
                    if (CollUtil.isEmpty(streamRecord.getBefore()) && CollUtil.isEmpty(streamRecord.getAfter())) {
                        log.warn("before和after无数据,跳过处理:" + sourceRecordChangeValue);
                        return;
                    }
                    String schema = null;
                    String tableName = null;
                    String[] topicSplit = topic.split("\\.");
                    if (topicSplit.length > 2) {
                        schema = topicSplit[1];
                        tableName = topicSplit[2];
                    }
                    streamRecord.setSchema(schema);
                    streamRecord.setTable(tableName);
                    streamRecord.setOperation(operation);
                    if (this.isDebug()) {
                        log.debug("DebeziumChangeRecord:{}", JSON.toJSONString(streamRecord));
                    }
                    changeRecord.add(streamRecord);
                }
            }
        });
        if (!changeRecord.isEmpty()) {
            List<List<FlowComponent>> next = this.next();
            if (CollUtil.isNotEmpty(next)) {
                log.info("DebeziumFlowComponent:{} 变更事件数量:{}", this.getKey(), changeRecord.size());
                // 监控
                this.flowComponentMonitor.processNumber(this, changeRecord.size());
                Tuple2<Timer, Timer.Sample> timerSampleTuple2 = this.flowComponentMonitor.runTimer(this);
                // 传输
                Transmit nextTransmit = new Transmit();
                nextTransmit.setFlowComponent(this);
                nextTransmit.setRecord(changeRecord);
                try {
                    this.runNext(nextTransmit, context);
                    if (timerSampleTuple2 != null) {
                        timerSampleTuple2.getT2().stop(timerSampleTuple2.getT1());
                    }
                } catch (Exception e) {
                    log.error("监听后续流程执行失败", e);
                    this.flowComponentMonitor.runError(this);
                    Throwable rootCause = ExceptionUtil.getRootCause(e);
                    // 中断流程
                    throw new FlowRunNextException("监听后续流程执行失败", rootCause);
                }
            }
        }
        // 标记这一批处理完成
        recordChangeEvents.forEach(f -> {
            try {
                recordCommitter.markProcessed(f);
            } catch (InterruptedException e) {
                log.error("保存点标记处理失败", e);
                throw new RuntimeException(e);
            }
        });
        recordCommitter.markBatchFinished();
    }

    /**
     * 将变更的行封装为Map
     *
     * @param fields 字段
     * @param struct 结构体
     * @return map
     */
    private Map<String, Object> getStringObjectMap(List<Field> fields, Struct struct) {
        // 返回LinkedHashMap
        return fields.stream()
                .map(Field::name)
                .filter(fieldName -> struct.getWithoutDefault(fieldName) != null)
                .map(fieldName -> {
                    Object object = struct.getWithoutDefault(fieldName);
                    if (object instanceof Double) {
                        object = BigDecimal.valueOf((Double) object);
                    }
                    return Pair.of(fieldName, object);
                })
                .collect(toMap(Pair::getKey, Pair::getValue, (v1, v2) -> v1, LinkedHashMap::new));
    }

    /**
     * 根据同步的表数量，来获取线程数
     *
     * @return 线程数
     */
    private int getThreads() {
        if (this.threadNumber != null) {
            return this.threadNumber;
        }
        int length = this.tables.split(",").length;
        // 如果大于10个表，使用4个线程
        if (length > 10) {
            return 4;
        }
        // 如果大于5个表使用3个线程
        if (length > 5) {
            return 3;
        }
        // 如果大于2个表使用2个线程
        if (length > 2) {
            return 2;
        }
        // 否则使用1个线程
        return 1;
    }

    /**
     * 停止
     */
    @Override
    public synchronized void stop() {
        log.info("关闭DebeziumEngine:" + this.getKey());
        if (this.debeziumEngine != null) {
            try {
                DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> currentDebeziumEngine = this.debeziumEngine;
                // 防止重复关闭,先把此变量设置为null
                this.debeziumEngine = null;
                // 后关闭
                currentDebeziumEngine.close();
                log.info("DebeziumEngine关闭完成");
            } catch (IllegalStateException e) {
                log.info("关闭DebeziumEngine失败:" + e.getMessage());
            } catch (Exception e) {
                log.error("关闭DebeziumEngine失败", e);
            }
        }
        // 优化，只允许运行的节点进行移除操作，其他节点调用stop时，不能影响正在运行的节点
        RBucket<FlowComponentOnly> flowComponentOnlyRBucket = this.redissonClient.getBucket(RedisKey.FLOW_COMPONENT_ONLY.build(this.getKey()));
        FlowComponentOnly flowComponentOnly = flowComponentOnlyRBucket.get();
        if (flowComponentOnly != null) {
            String instanceId = flowComponentOnly.getInstanceId();
            String currentInstanceId = this.serverManager.instanceId();
            log.info("DebeziumFlowComponent:{} 停止时运行实例:{},当前实例:{}", this.getKey(), instanceId, currentInstanceId);
            if (Objects.equals(instanceId, currentInstanceId)) {
                // 移除运行标识
                try {
                    flowComponentOnlyRBucket.delete();
                    log.info("DebeziumFlowComponent:{} 运行状态已移除", this.getKey());
                } catch (Exception e) {
                    log.error("DebeziumFlowComponent:{} 运行状态移除失败", this.getKey(), e);
                }
            }
        }
        // 修复这里释放锁导致的bug，当组件触发重启时，多个节点接收到重启消息，各个节点都先调用stop，再次start
        // 导致所有节点都进行了执行
        // 开始关闭线程
        if (this.executeFuture != null) {
            try {
                this.executeFuture.cancel(false);
                this.executeFuture = null;
            } catch (Exception e) {
                log.error("关闭DebeziumEngine-Future失败", e);
            }
        }
        if (this.execute != null) {
            this.execute.shutdown();
            this.execute = null;
        }
    }

}
