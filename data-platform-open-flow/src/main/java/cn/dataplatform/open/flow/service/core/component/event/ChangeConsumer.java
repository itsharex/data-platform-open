package cn.dataplatform.open.flow.service.core.component.event;

import cn.dataplatform.open.flow.service.core.Context;
import cn.dataplatform.open.flow.service.core.Flow;
import cn.dataplatform.open.flow.service.core.Transmit;
import cn.dataplatform.open.flow.service.core.component.FlowComponent;
import cn.dataplatform.open.flow.service.core.component.event.convert.MongoDataConverter;
import cn.dataplatform.open.flow.service.core.record.BatchStreamRecord;
import cn.dataplatform.open.flow.service.core.record.StreamRecord;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import io.debezium.data.Envelope;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.debezium.data.Envelope.FieldName.*;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/12/15
 * @since 1.0.0
 */
@Slf4j
public class ChangeConsumer implements DebeziumEngine.ChangeConsumer<ChangeEvent<SourceRecord, SourceRecord>> {

    private final Context context;
    private final DebeziumFlowComponent debeziumFlowComponent;
    private final Map<String, String> mdcContextMap;

    public ChangeConsumer(DebeziumFlowComponent debeziumFlowComponent, Context context,
                          Map<String, String> mdcContextMap) {
        this.debeziumFlowComponent = debeziumFlowComponent;
        this.context = context;
        this.mdcContextMap = mdcContextMap;
    }

    /**
     * 处理变更事件
     *
     * @param recordChangeEvents 变更事件
     * @param recordCommitter    提交者
     */
    @Override
    public void handleBatch(List<ChangeEvent<SourceRecord, SourceRecord>> recordChangeEvents,
                            DebeziumEngine.RecordCommitter<ChangeEvent<SourceRecord, SourceRecord>> recordCommitter)
            throws InterruptedException {
        try {
            // 修复链路追踪丢失问题
            MDC.setContextMap(mdcContextMap);
            this.handlePayload(recordChangeEvents, recordCommitter);
        } finally {
            MDC.clear();
        }
    }

    /**
     * 处理变更事件
     *
     * @param recordChangeEvents 变更事件
     * @param recordCommitter    提交者
     */
    public void handlePayload(List<ChangeEvent<SourceRecord, SourceRecord>> recordChangeEvents,
                              DebeziumEngine.RecordCommitter<ChangeEvent<SourceRecord, SourceRecord>> recordCommitter)
            throws InterruptedException {
        Flow flow = this.debeziumFlowComponent.getFlow();
        if (!flow.isRunning()) {
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
                        if (flow.isDebug()) {
                            log.debug("数据监听:{},心跳事件,当前时间戳:{}", this.debeziumFlowComponent.getKey(), tsMsValue);
                        }
                        return;
                    }
                } else {
                    operation = Envelope.Operation.forCode((String) sourceRecordChangeValue.get(operationField));
                }
                // 如果没有这个key报错,直接跳过,有可能是表结构变化
                if (operation == null) {
                    log.warn("数据监听:{},未找到操作类型,跳过处理:{}", this.debeziumFlowComponent.getKey(), sourceRecordChangeValue);
                    return;
                }
                if (this.debeziumFlowComponent.getOperations().contains(operation)) {
                    StreamRecord streamRecord = new StreamRecord();
                    // 获取增删改对应的结构体数据
                    Object beforeObject = sourceRecordChangeValue.get(BEFORE);
                    if (beforeObject instanceof Struct struct) {
                        List<Field> fields = struct.schema().fields();
                        // 将变更的行封装为Map
                        Map<String, Object> payload = this.extractNonNullFields(fields, struct);
                        streamRecord.setBefore(payload);
                    } else if (beforeObject instanceof String string) {
                        // mongoDB
                        streamRecord.setBefore(MongoDataConverter.convert(string, this.debeziumFlowComponent.getConnectionTimeZone()));
                    }
                    Object afterObject = sourceRecordChangeValue.get(AFTER);
                    if (afterObject instanceof Struct struct) {
                        List<Field> fields = struct.schema().fields();
                        // 将变更的行封装为Map
                        Map<String, Object> payload = this.extractNonNullFields(fields, struct);
                        streamRecord.setAfter(payload);
                    } else if (afterObject instanceof String string) {
                        // mongoDB
                        streamRecord.setAfter(MongoDataConverter.convert(string, this.debeziumFlowComponent.getConnectionTimeZone()));
                    }
                    // 如果before after都没有数据时
                    // 例如mongodb默认没有开启changeStreamPreAndPostImages，则before没有数据，删除时都是空的
                    if (CollUtil.isEmpty(streamRecord.getBefore()) && CollUtil.isEmpty(streamRecord.getAfter())) {
                        log.warn("数据监听before和after无数据,跳过处理:" + sourceRecordChangeValue);
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
                    if (this.debeziumFlowComponent.isDebug()) {
                        log.debug("数据监听:{} 变更记录:{}", this.debeziumFlowComponent.getKey(), JSON.toJSONString(streamRecord));
                    }
                    changeRecord.add(streamRecord);
                }
            }
        });
        if (!changeRecord.isEmpty()) {
            List<List<FlowComponent>> next = this.debeziumFlowComponent.next();
            if (CollUtil.isNotEmpty(next)) {
                log.info("数据监听:{} 变更事件数量:{}", this.debeziumFlowComponent.getKey(), changeRecord.size());
                // 往下游传输数据
                Transmit nextTransmit = new Transmit();
                nextTransmit.setFlowComponent(this.debeziumFlowComponent);
                nextTransmit.setRecord(changeRecord);
                this.debeziumFlowComponent.runNext(nextTransmit, this.context);
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
    private Map<String, Object> extractNonNullFields(List<Field> fields, Struct struct) {
        if (fields == null || fields.isEmpty() || struct == null) {
            return new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>(fields.size());
        for (Field field : fields) {
            String fieldName = field.name();
            Object value = struct.getWithoutDefault(fieldName);
            if (value != null) {
                if (value instanceof Double) {
                    value = BigDecimal.valueOf((Double) value);
                }
                result.put(fieldName, value);
            }
        }
        return result;
    }

}
