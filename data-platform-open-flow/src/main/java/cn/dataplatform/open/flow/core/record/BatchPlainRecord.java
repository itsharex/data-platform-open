package cn.dataplatform.open.flow.core.record;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 〈PlainRecord〉
 *
 * @author dqw
 * @date 2025/1/8
 * @since 1.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchPlainRecord implements BatchRecord {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<PlainRecord> records = new ArrayList<>();

    @Override
    public int size() {
        return this.records.size();
    }

    /**
     * 添加记录
     *
     * @param record 记录
     */
    @Override
    public void add(Record record) {
        if (record instanceof BatchPlainRecord batchPlainRecord) {
            this.records.addAll(batchPlainRecord.getRecords());
        } else if (record instanceof PlainRecord plainRecord) {
            this.records.add(plainRecord);
        } else {
            throw new IllegalArgumentException("record type error");
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
     * 获取记录
     *
     * @return 记录
     */
    @Override
    public List<PlainRecord> getRecords() {
        return this.records;
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
     * 获取最后一条记录
     *
     * @return /
     */
    @JsonIgnore
    @JSONField(serialize = false)
    public Map<String, Object> getLast() {
        if (CollUtil.isEmpty(this.records)) {
            throw new IllegalArgumentException("records is empty");
        }
        PlainRecord last = this.records.getLast();
        if (last == null) {
            throw new IllegalArgumentException("records is empty");
        }
        return last.getRow();
    }

}