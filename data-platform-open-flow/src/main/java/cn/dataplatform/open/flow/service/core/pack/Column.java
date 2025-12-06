package cn.dataplatform.open.flow.service.core.pack;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/6
 * @since 1.0.0
 */
@Data
@AllArgsConstructor
public class Column implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final int type;

    private final String typeName;

}
