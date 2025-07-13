package cn.dataplatform.open.flow.core.record;

import java.util.List;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/1/26
 * @since 1.0.0
 */
public interface BatchRecord extends Record {

    /**
     * 添加记录
     *
     * @param record 记录
     */
    void add(Record record);

    /**
     * 设置多个记录
     *
     * @param records 记录列表
     */
    void addAll(List<? extends Record> records);

    /**
     * 获取记录
     *
     * @return 记录
     */
    List<? extends Record> getRecords();


    /**
     * 根据记录类型，生成一个新的批量对象
     *
     * @param recordType 记录类型
     * @return 批量记录
     */
    static BatchRecord newInstance(Class<? extends Record> recordType) {
        return BatchRecord.newInstance(recordType, 10);
    }

    /**
     * 根据记录类型，生成一个新的批量对象
     *
     * @param recordType      记录类型
     * @param initialCapacity 初始容量
     * @return 批量记录
     */
    static BatchRecord newInstance(Class<? extends Record> recordType, int initialCapacity) {
        if (recordType == null) {
            throw new IllegalArgumentException("记录类型不能为空");
        }
        // 如果是StreamRecord 或者 BatchStreamRecord 返回 BatchStreamRecord
        if (StreamRecord.class.isAssignableFrom(recordType)
                || BatchStreamRecord.class.isAssignableFrom(recordType)) {
            return new BatchStreamRecord(initialCapacity);
        }
        // 如果是PlainRecord 或者 BatchPlainRecord 统一返回 BatchPlainRecord
        if (PlainRecord.class.isAssignableFrom(recordType)
                || BatchPlainRecord.class.isAssignableFrom(recordType)) {
            return new BatchPlainRecord(initialCapacity);
        }
        throw new IllegalArgumentException("记录类型不支持");
    }


}
