package cn.dataplatform.open.support.service.impl;

import cn.dataplatform.open.common.enums.ServerName;
import cn.dataplatform.open.common.server.Server;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.support.config.PrometheusDiscoveryConfig;
import cn.dataplatform.open.support.service.PrometheusDiscoveryService;
import cn.dataplatform.open.support.vo.prometheus.PrometheusTarget;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/5
 * @since 1.0.0
 */
@Service
public class PrometheusDiscoveryServiceImpl implements PrometheusDiscoveryService {

    @Resource
    private ServerManager serverManager;
    @Resource
    private PrometheusDiscoveryConfig prometheusDiscoveryConfig;

    /**
     * 获取所有的 Prometheus 目标
     *
     * @return Prometheus 目标列表
     */
    @Override
    public List<PrometheusTarget> getAllTargets() {
        // 获取所有服务类型的列表
        List<String> serviceNames = Arrays.stream(ServerName.values()).map(ServerName::getValue).toList();
        String scrapeInterval = this.prometheusDiscoveryConfig.getScrapeInterval();
        Map<String, String> metricsPath = this.prometheusDiscoveryConfig.getMetricsPath();
        List<PrometheusTarget> targets = new ArrayList<>(serviceNames.size());
        for (String serviceName : serviceNames) {
            Collection<Server> servers = this.serverManager.availableList(serviceName);
            if (servers.isEmpty()) {
                // 没有可用的服务实例
                continue;
            }
            List<String> instants = new ArrayList<>(servers.size());
            for (Server server : servers) {
                // instants.add("host.docker.internal" + ":" + server.getPort());
                instants.add(server.getHost() + ":" + server.getPort());
            }
            PrometheusTarget prometheusTarget = new PrometheusTarget();
            prometheusTarget.setTargets(instants);
            // labels.put("job", serviceName);
            String path = metricsPath.getOrDefault(serviceName, "/actuator/prometheus");
            Map<String, String> labels = new HashMap<>(2);
            labels.put("__metrics_path__", path);
            // __scrape_interval__
            labels.put("__scrape_interval__", scrapeInterval);
            prometheusTarget.setLabels(labels);
            targets.add(prometheusTarget);
        }
        return targets;
    }

}
