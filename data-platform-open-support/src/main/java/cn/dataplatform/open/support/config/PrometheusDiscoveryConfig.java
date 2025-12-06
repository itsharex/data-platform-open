package cn.dataplatform.open.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/12/4
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "dp.prometheus.discovery")
public class PrometheusDiscoveryConfig {

    /**
     * 抓取间隔
     */
    private String scrapeInterval = "10s";

    /**
     * data-platform-flow = /dp-flow/actuator/prometheus
     * data-platform-web = /dp-web/actuator/prometheus
     * ...
     */
    private Map<String, String> metricsPath = new HashMap<>();

}
