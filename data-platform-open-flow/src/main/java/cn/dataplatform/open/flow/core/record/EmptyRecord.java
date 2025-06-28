package cn.dataplatform.open.flow.core.record;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serial;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/10
 * @since 1.0.0
 */
public class EmptyRecord implements Record {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final EmptyRecord INSTANCE = new EmptyRecord();

    @Override
    public int size() {
        return 0;
    }

    @JsonIgnore
    @Override
    public boolean isEmpty() {
        return true;
    }

}
