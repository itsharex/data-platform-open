package cn.dataplatform.open.flow.vo.data.flow;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/24
 * @since 1.0.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class FlowComponentOnly implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String instanceId;

    /**
     * 组件启动时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

}
