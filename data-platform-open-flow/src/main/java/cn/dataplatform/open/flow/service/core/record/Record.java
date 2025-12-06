package cn.dataplatform.open.flow.service.core.record;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/**
 * 〈Record〉
 *
 * @author dqw
 * @date 2025/1/8 14:00
 * @since 1.0.0
 */
public interface Record extends Serializable {

    /**
     * 数据大小
     *
     * @return size
     */
    int size();

    /**
     * 是否为空记录
     *
     * @return true/false
     */
    @JsonIgnore
    default boolean isEmpty() {
        return size() == 0;
    }


    enum Op {
        /**
         * 更新
         */
        UPDATE,
        /**
         * 插入
         */
        INSERT,
        /**
         * 删除
         */
        DELETE
    }

}