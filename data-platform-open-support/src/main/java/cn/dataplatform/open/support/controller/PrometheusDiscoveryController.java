package cn.dataplatform.open.support.controller;

import cn.dataplatform.open.support.service.PrometheusDiscoveryService;
import cn.dataplatform.open.support.vo.prometheus.PrometheusTarget;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/5
 * @since 1.0.0
 */
@RestController
@RequestMapping("/prometheus-sd")
public class PrometheusDiscoveryController {

    @Resource
    private PrometheusDiscoveryService prometheusDiscoveryService;


    /**
     * 获取所有的 Prometheus 目标
     * <p>
     * localhost:9700/dp-support/prometheus-sd/targets
     *
     * @return Prometheus 目标列表
     */
    @GetMapping("/targets")
    public List<PrometheusTarget> getAllTargets() {
        return this.prometheusDiscoveryService.getAllTargets();
    }

}
