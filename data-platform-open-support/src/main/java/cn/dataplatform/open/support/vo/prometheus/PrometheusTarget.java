package cn.dataplatform.open.support.vo.prometheus;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/5
 * @since 1.0.0
 */
@Data
public class PrometheusTarget {

    private List<String> targets;

    private Map<String, String> labels;

}
