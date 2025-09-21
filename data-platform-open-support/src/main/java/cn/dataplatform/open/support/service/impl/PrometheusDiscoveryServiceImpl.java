package cn.dataplatform.open.support.service.impl;

import cn.dataplatform.open.common.enums.ServerName;
import cn.dataplatform.open.common.enums.ServerStatus;
import cn.dataplatform.open.common.server.Server;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.support.service.PrometheusDiscoveryService;
import cn.dataplatform.open.support.vo.prometheus.PrometheusTarget;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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
@Service
public class PrometheusDiscoveryServiceImpl implements PrometheusDiscoveryService {

    /**
     * 刷新间隔
     */
    @Value("${dp.prometheus.scrape-interval:10s}")
    private String scrapeInterval;

    @Resource
    private ServerManager serverManager;

    /**
     * 获取所有的 Prometheus 目标
     *
     * @return Prometheus 目标列表
     */
    @Override
    public List<PrometheusTarget> getAllTargets() {
        List<PrometheusTarget> targets = new ArrayList<>();
        // 获取所有服务类型的列表
        List<String> serviceTypes = List.of(ServerName.WEB_SERVER.getValue(), ServerName.FLOW_SERVER.getValue(),
                ServerName.QUERY_SERVER.getValue(), ServerName.SUPPORT_SERVER.getValue());
        for (String serviceName : serviceTypes) {
            PrometheusTarget prometheusTarget = new PrometheusTarget();
            List<String> instants = new ArrayList<>();
            Map<String, String> labels = new HashMap<>();
            for (Server server : this.serverManager.list(serviceName)) {
                if (server.getStatus() != ServerStatus.ONLINE) {
                    continue;
                }
                // server.getHost()
                instants.add("host.docker.internal" + ":" + server.getPort());
            }
            prometheusTarget.setTargets(instants);
            // labels.put("job", serviceName);
            labels.put("__metrics_path__", this.getPrometheusPath(serviceName));
            // __scrape_interval__
            labels.put("__scrape_interval__", this.scrapeInterval);
            prometheusTarget.setLabels(labels);
            targets.add(prometheusTarget);
        }
        return targets;
    }

    /**
     * 获取 Prometheus 目标路径
     *
     * @return Prometheus 目标对象
     */
    private String getPrometheusPath(String sn) {
        ServerName serverName = ServerName.getByValue(sn);
        // 根据服务类型设置metrics_path
        return switch (serverName) {
            case WEB_SERVER -> "/dp-web/actuator/prometheus";
            case FLOW_SERVER -> "/dp-flow/actuator/prometheus";
            case QUERY_SERVER -> "/dp-query/actuator/prometheus";
            case SUPPORT_SERVER -> "/dp-support/actuator/prometheus";
        };
    }

}
