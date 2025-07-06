package cn.dataplatform.open.support.service;

import cn.dataplatform.open.support.vo.prometheus.PrometheusTarget;

import java.util.List;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/5
 * @since 1.0.0
 */
public interface PrometheusDiscoveryService {

    /**
     * 获取所有的 Prometheus 目标
     *
     * @return Prometheus 目标列表
     */
    List<PrometheusTarget> getAllTargets();

}
