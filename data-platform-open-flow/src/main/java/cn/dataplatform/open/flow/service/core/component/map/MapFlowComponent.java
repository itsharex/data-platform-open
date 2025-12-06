package cn.dataplatform.open.flow.service.core.component.map;

import cn.dataplatform.open.common.groovy.GroovySupport;
import cn.dataplatform.open.flow.service.core.Context;
import cn.dataplatform.open.flow.service.core.Flow;
import cn.dataplatform.open.flow.service.core.Transmit;
import cn.dataplatform.open.flow.service.core.component.FlowComponent;
import cn.dataplatform.open.flow.service.core.record.Record;
import cn.dataplatform.open.flow.service.core.record.*;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import groovy.lang.Binding;
import groovy.lang.MissingPropertyException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/1/6
 * @since 1.0.0
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class MapFlowComponent extends FlowComponent {

    /**
     * 脚本缓存
     */
    private static final Map<String, Class<?>> SCRIPT_CACHE = new ConcurrentHashMap<>();

    /**
     * 映射后是否保留源字段
     */
    private boolean retainOriginalField = false;

    /**
     * 字段映射,字段映射是一个map,key为源字段,value为目标字段
     * <p>
     * 适合例如mysql同步到es,字段不一致,或者需要下划线转驼峰时使用
     */
    private Map<String, String> fieldMapping = new LinkedHashMap<>();

    /**
     * 值的特殊处理,与表达式映射,key为源字段,value为表达式
     * 例如支持值转大写,格式化日期,字符串转数字,字符串拼接,自定义值,获取当前时间,groovy表达式等
     */
    private Map<String, String> valueMapping = new LinkedHashMap<>();


    public MapFlowComponent(Flow flow, String code) {
        super(flow, code);
    }

    /**
     * 处理转换数据
     *
     * @param transmit 数据
     * @param context  上下文
     */
    @Override
    public void run(Transmit transmit, Context context) {
        Record record = transmit.getRecord();
        if (record.isEmpty()) {
            return;
        }
        Record newRecord;
        // 如果有配置属性或者值映射时
        if (!this.fieldMapping.isEmpty() || !this.valueMapping.isEmpty()) {
            newRecord = switch (record) {
                case BatchStreamRecord batchStreamRecord -> this.map(batchStreamRecord);
                case BatchPlainRecord batchPlainRecord -> this.map(batchPlainRecord);
                case StreamRecord streamRecord -> this.map(streamRecord);
                case PlainRecord plainRecord -> this.map(plainRecord);
                default -> throw new UnsupportedOperationException("暂不支持:" + record.getClass());
            };
        } else {
            newRecord = record;
        }
        this.runNext(() -> {
            Transmit nextTransmit = new Transmit();
            nextTransmit.setRecord(newRecord);
            nextTransmit.setFlowComponent(this);
            return nextTransmit;
        }, context);
    }

    /**
     * 字段映射
     *
     * @param record record
     * @return 映射后的数据
     */
    private StreamRecord map(StreamRecord record) {
        // 不能修改原来的值,存在多个相同级的流程,会影响到其他的
        StreamRecord changeRecord = new StreamRecord();
        changeRecord.setBefore(record.getBefore());
        changeRecord.setAfter(record.getAfter());
        Map<String, Object> before = record.getBefore();
        if (CollUtil.isNotEmpty(before)) {
            Map<String, Object> newBefore = this.map(before);
            // 存储新的值
            changeRecord.setBefore(newBefore);
        }
        Map<String, Object> after = record.getAfter();
        if (CollUtil.isNotEmpty(after)) {
            Map<String, Object> newAfter = this.map(after);
            // 存储新的值
            changeRecord.setAfter(newAfter);
        }
        changeRecord.setSchema(record.getSchema());
        changeRecord.setTable(record.getTable());
        changeRecord.setOperation(record.getOperation());
        return changeRecord;
    }


    /**
     * 字段映射
     *
     * @param batchStreamRecord 批量流式记录
     * @return 映射后的数据
     */
    private Record map(BatchStreamRecord batchStreamRecord) {
        List<StreamRecord> records = batchStreamRecord.getRecords();
        // 处理after值,暂时不考虑before的
        List<StreamRecord> recordsNew = new ArrayList<>(records.size());
        records.forEach(record -> {
            StreamRecord newStreamRecord = this.map(record);
            recordsNew.add(newStreamRecord);
        });
        return new BatchStreamRecord(recordsNew);
    }


    /**
     * 字段映射
     *
     * @param record record
     * @return 映射后的数据
     */
    private PlainRecord map(PlainRecord record) {
        PlainRecord newPlainRecord = new PlainRecord();
        Map<String, Object> row = record.getRow();
        Map<String, Object> newRow = this.map(row);
        newPlainRecord.setRow(newRow);
        return newPlainRecord;
    }

    /**
     * 字段映射
     *
     * @param map 原数据
     * @return 映射后的数据
     */
    private Map<String, Object> map(Map<String, Object> map) {
        Map<String, Object> newMap = new LinkedHashMap<>(map.size());
        // groovy参数绑定
        Binding binding = new Binding(map);
        // 属性映射
        map.forEach((k, v) -> {
            Object convertValue = this.convertValue(k, v, binding);
            String newKey = this.fieldMapping.get(k);
            // 没有映射的字段
            if (newKey == null) {
                newMap.put(k, convertValue);
                return;
            }
            if (this.retainOriginalField) {
                newMap.put(k, convertValue);
            }
            // 新的存储一份
            newMap.put(newKey, convertValue);
        });
        // 值映射
        if (CollUtil.isEmpty(this.valueMapping)) {
            // 如果没有配置值映射,则直接返回
            return newMap;
        }
        Set<String> keySet = this.valueMapping.keySet();
        for (String string : keySet) {
            if (newMap.containsKey(string)) {
                // 排除掉已经存在的字段
                continue;
            }
            // 填充新字段
            Object convertValue = this.convertValue(string, null, binding);
            newMap.put(string, convertValue);
        }
        return newMap;
    }

    /**
     * 字段映射
     *
     * @param batchPlainRecord 批量普通记录
     * @return 映射后的数据
     */
    private Record map(BatchPlainRecord batchPlainRecord) {
        List<PlainRecord> records = batchPlainRecord.getRecords();
        List<PlainRecord> recordsNew = new ArrayList<>(records.size());
        records.forEach(record -> {
            PlainRecord newPlainRecord = this.map(record);
            recordsNew.add(newPlainRecord);
        });
        return new BatchPlainRecord(recordsNew);
    }

    /**
     * 转换值
     *
     * @param k       key
     * @param v       value
     * @param binding 绑定
     * @return 转换后的值
     */
    private Object convertValue(String k, Object v, Binding binding) {
        if (CollUtil.isEmpty(this.valueMapping)) {
            // 如果没有配置映射,则返回源值
            return v;
        }
        if (!this.valueMapping.containsKey(k)) {
            // 如果没有配置此属性,则返回源值
            return v;
        }
        String valueScriptString = this.valueMapping.get(k);
        if (StrUtil.isBlank(valueScriptString)) {
            // 如果设置值为null或者空字符串,直接返回
            return valueScriptString;
        }
        Class<?> aClass = SCRIPT_CACHE.computeIfAbsent(valueScriptString, GroovySupport::compile);
        try {
            return GroovySupport.run(aClass, binding);
        } catch (MissingPropertyException e) {
            // 设置的固定的值
            return valueScriptString;
        }
    }

    /**
     * 关闭此组件
     */
    @Override
    public synchronized void stop() {
        Collection<Class<?>> values = SCRIPT_CACHE.values();
        for (Class<?> value : values) {
            InvokerHelper.removeClass(value);
        }
        SCRIPT_CACHE.clear();
    }

}
