package cn.dataplatform.open.flow.core.record;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Map;

/**
 * 〈PlainRecord〉
 *
 * @author dqw
 * @date 2025/1/10
 * @since 1.0.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PlainRecord implements Record {

    @Serial
    private static final long serialVersionUID = 1L;

    private Map<String, Object> row;

    /**
     * 数据大小
     *
     * @return size
     */
    @Override
    public int size() {
        if (this.row == null) {
            return 0;
        }
        return 1;
    }

}