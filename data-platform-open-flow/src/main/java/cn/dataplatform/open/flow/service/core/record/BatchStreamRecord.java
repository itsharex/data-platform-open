package cn.dataplatform.open.flow.service.core.record;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 〈BatchStreamRecord〉
 *
 * @author dqw
 * @date 2025/1/10
 * @since 1.0.0
 */
@Data
public class BatchStreamRecord implements BatchRecord {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<StreamRecord> records;

    public BatchStreamRecord(int initialCapacity) {
        this.records = new ArrayList<>(initialCapacity);
    }

    public BatchStreamRecord() {
        this.records = new ArrayList<>();
    }

    public BatchStreamRecord(List<StreamRecord> records) {
        this.records = records;
    }

    public BatchStreamRecord(StreamRecord streamRecord) {
        this.records = new ArrayList<>(1);
        this.records.add(streamRecord);
    }

    /**
     * 添加记录
     *
     * @param record 记录
     */
    @Override
    public void add(Record record) {
        if (record == null) {
            return;
        }
        if (record instanceof BatchStreamRecord batchStreamRecord) {
            this.records.addAll(batchStreamRecord.getRecords());
        } else if (record instanceof StreamRecord streamRecord) {
            this.records.add(streamRecord);
        } else {
            throw new IllegalArgumentException("记录类型错误,应该是StreamRecord或BatchStreamRecord,当前为: " + record.getClass().getName());
        }
    }

    /**
     * 设置多个记录
     *
     * @param records 记录列表
     */
    @Override
    public void addAll(List<? extends Record> records) {
        records.forEach(this::add);
    }

    /**
     * 数据大小
     *
     * @return size
     */
    @Override
    public int size() {
        return this.records.size();
    }

    /**
     * 是否为空记录
     *
     * @return true/false
     */
    @JsonIgnore
    @Override
    public boolean isEmpty() {
        return CollUtil.isEmpty(this.records);
    }

    /**
     * 获取记录
     *
     * @return 记录
     */
    @Override
    public List<StreamRecord> getRecords() {
        return this.records;
    }

}