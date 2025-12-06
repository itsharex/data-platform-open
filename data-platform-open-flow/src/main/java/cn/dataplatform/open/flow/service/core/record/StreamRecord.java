package cn.dataplatform.open.flow.service.core.record;

import io.debezium.data.Envelope;
import lombok.Data;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/**
 * 〈RecordChange〉
 *
 * @author dqw
 * @date 2025/1/8
 * @since 1.0.0
 */
@Data
public class StreamRecord implements Record {

    @Serial
    private static final long serialVersionUID = 1L;

    private String schema;

    private String table;

    /**
     * 操作类型
     */
    private Op operation;
    /**
     * 变更前数据
     */
    private Map<String, Object> before;

    /**
     * 变更后数据
     */
    private Map<String, Object> after;


    /**
     * 设置操作类型，根据debezium的Envelope.Operation类型转换为本地的Op类型
     *
     * @param operation 操作类型
     */
    public void setOperation(Envelope.Operation operation) {
        Objects.requireNonNull(operation);
        switch (operation) {
            case UPDATE -> this.operation = Op.UPDATE;
            case DELETE -> this.operation = Op.DELETE;
            // 默认是INSERT
            default -> this.operation = Op.INSERT;
        }
    }

    /**
     * 设置操作类型
     *
     * @param operation 操作类型
     */
    public void setOperation(Op operation) {
        Objects.requireNonNull(operation);
        this.operation = operation;
    }

    /**
     * 数据大小
     *
     * @return size
     */
    @Override
    public int size() {
        // 如果after或者before为空,返回0
        if (this.before == null && this.after == null) {
            return 0;
        }
        return 1;
    }

}