package cn.dataplatform.open.common.vo.alarm.robot;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/22
 * @since 1.0.0
 */
@Data
public class Silent {

    /**
     * 沉默关键词,遇到则在判断日期
     */
    private Set<String> keys;

    /**
     * 如果为空,则一直有效
     */
    private LocalDateTime expire;
}
