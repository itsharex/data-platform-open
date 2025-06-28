package cn.dataplatform.open.flow.core.record;

import io.debezium.data.Envelope;
import lombok.Data;

import java.io.Serial;
import java.util.Map;

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
    private Envelope.Operation operation;
    /**
     * 变更前数据
     */
    private Map<String, Object> before;

    /**
     * 变更后数据
     */
    private Map<String, Object> after;

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