/*
 * ============================================================================
 *
 *                    数海文舟 (DATA PLATFORM) 版权所有 © 2025
 *
 *       本软件受著作权法和国际版权条约保护。
 *       未经明确书面授权，任何单位或个人不得对本软件进行复制、修改、分发、
 *       逆向工程、商业用途等任何形式的非法使用。违者将面临人民币100万元的
 *       法定罚款及可能的法律追责。
 *
 *       举报侵权行为可获得实际罚款金额40%的现金奖励。
 *       法务邮箱：761945125@qq.com
 *
 *       COPYRIGHT (C) 2025 dingqianwen COMPANY. ALL RIGHTS RESERVED.
 *
 * ============================================================================
 */
package cn.dataplatform.open.flow.core;

import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.enums.RedisKey;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.common.util.ParallelStreamUtils;
import cn.dataplatform.open.common.vo.flow.FlowHeartbeat;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.thread.ThreadUtil;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * 流程引擎
 * <p>
 * Destroy method on bean with name 'flowEngine' threw an
 * * exception: org.redisson.RedissonShutdownException: Redisson is shutdown 解决 @DependsOn("redisson")
 * <p>
 * redisson、sourceManager 与 flowEngine 之间存在依赖关系,redisson、sourceManager 会在 flowEngine 之后关闭
 *
 * @author dingqianwen
 * @date 2025/1/4
 * @since 1.0.0
 */
@DependsOn({"redisson", "sourceManager",
        // 保存点等使用
        "dataSource",
        "serverManager"})
@Slf4j
@Component
public class FlowEngine implements InitializingBean {

    /**
     * 数据流心跳使用
     */
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    /**
     * 关闭数据流使用，因为有些数据流关闭时间较长，例如使用了监听等
     */
    private final ThreadPoolExecutor flowStopExecutor = ThreadUtil.newExecutor(2, 20, Integer.MAX_VALUE);

    /**
     * 工作空间 -> 流程编码 -> 流程
     */
    private final Map<String, Map<String, Flow>> flows = new ConcurrentHashMap<>();

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ServerManager serverManager;

    /**
     * 添加流程
     *
     * @param flow 流程
     */
    public void addFlow(Flow flow) {
        Map<String, Flow> flowMap = this.flows.computeIfAbsent(flow.getWorkspaceCode(),
                k -> new ConcurrentHashMap<>()
        );
        flow.setAddTime(LocalDateTime.now());
        flowMap.put(flow.getCode(), flow);
    }

    /**
     * 获取流程
     *
     * @param workspace 工作空间
     * @param flowCode  流程编码
     * @return Flow
     */
    public Flow getFlow(String workspace, String flowCode) {
        Map<String, Flow> flowMap = this.flows.get(workspace);
        if (flowMap == null) {
            return null;
        }
        return flowMap.get(flowCode);
    }

    /**
     * 移除流程,如果流程正在运行,会停止
     *
     * @param workspace 工作空间
     * @param flowCode  流程编码
     */
    public void removeFlow(String workspace, String flowCode) {
        Map<String, Flow> flowMap = this.flows.get(workspace);
        if (flowMap == null) {
            return;
        }
        Flow flow = flowMap.remove(flowCode);
        if (flow == null) {
            return;
        }
        if (flow.isRunning()) {
            log.info("准备停止流程,流程编码:{},实例:{}", flow.getCode(), this.serverManager.instanceId());
            // 例如debezium 汇聚管道刷最后的数据停止时间较长，需要等待
            Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
            Future<?> submit = this.flowStopExecutor.submit(() -> {
                if (copyOfContextMap != null) {
                    MDC.setContextMap(copyOfContextMap);
                }
                try {
                    flow.stop();
                } finally {
                    MDC.clear();
                }
            });
            try {
                // 最多等待6分钟
                submit.get(6, TimeUnit.MINUTES);
                log.info("流程:{} 停止成功,实例:{}", flow.getCode(), this.serverManager.instanceId());
            } catch (Exception e) {
                log.error("停止流程异常,流程:{},实例:{}", flow.getCode(), this.serverManager.instanceId(), e);
            }
            String key = String.format("%s-%s", workspace, flowCode);
            RMap<String, FlowHeartbeat> rMap = this.redissonClient.getMap(RedisKey.FLOW_HEARTBEAT.build(key));
            rMap.remove(this.serverManager.instanceId());
        }
    }

    /**
     * 销毁
     */
    @PreDestroy
    @Order(Integer.MIN_VALUE)
    public void destroy() {
        log.info("关闭流程引擎");
        // 关闭流程
        Collection<Map<String, Flow>> values = this.flows.values();
        if (!values.isEmpty()) {
            for (Map<String, Flow> value : values) {
                Collection<Flow> collection = value.values();
                ParallelStreamUtils.forEach(collection, flow -> {
                    flow.stop();
                    String workspaceCode = flow.getWorkspaceCode();
                    String flowCode = flow.getCode();
                    String key = String.format("%s-%s", workspaceCode, flowCode);
                    RMap<String, FlowHeartbeat> rMap = this.redissonClient.getMap(RedisKey.FLOW_HEARTBEAT.build(key));
                    rMap.remove(this.serverManager.instanceId());
                }, false);
            }
        }
        // 关闭线程池
        this.heartbeatExecutor.shutdown();
        this.flowStopExecutor.shutdown();
        log.info("关闭流程引擎成功");
    }


    /**
     * 流程引擎初始化
     */
    @Override
    public void afterPropertiesSet() {
        Runnable runnable = () -> {
            MDC.put(Constant.REQUEST_ID, UUID.fastUUID().toString(true));
            try {
                Collection<Map<String, Flow>> values = this.flows.values();
                if (values.isEmpty()) {
                    return;
                }
                for (Map<String, Flow> value : values) {
                    Collection<Flow> collection = value.values();
                    for (Flow flow : collection) {
                        MDC.put(Constant.FLOW_CODE, flow.getCode());
                        try {
                            boolean running = flow.isRunning();
                            if (!running) {
                                continue;
                            }
                            // 更新流程心跳
                            String key = String.format("%s-%s", flow.getWorkspaceCode(), flow.getCode());
                            RMap<String, FlowHeartbeat> rMap = this.redissonClient.getMap(RedisKey.FLOW_HEARTBEAT.build(key));
                            // 查找并更新当前实例的心跳记录
                            FlowHeartbeat flowHeartbeat = rMap.get(this.serverManager.instanceId());
                            // 如果没有找到记录，则添加新的心跳记录
                            if (flowHeartbeat == null) {
                                FlowHeartbeat newHeartbeat = new FlowHeartbeat();
                                newHeartbeat.setInstanceId(this.serverManager.instanceId());
                                newHeartbeat.setFastHeartbeat(LocalDateTime.now());
                                newHeartbeat.setLastHeartbeat(LocalDateTime.now());
                                rMap.put(this.serverManager.instanceId(), newHeartbeat);
                            } else {
                                // 更新心跳
                                flowHeartbeat.setLastHeartbeat(LocalDateTime.now());
                                // 重新添加到集合中
                                rMap.put(this.serverManager.instanceId(), flowHeartbeat);
                            }
                            if (flow.isDebug()) {
                                log.debug("流程:{} 心跳,实例:{}", key, this.serverManager.instanceId());
                            }
                        } catch (Exception e) {
                            // 防止某个数据流出现问题，导致此次整个心跳异常
                            log.error("数据流心跳异常,数据流编码:{}-{}", flow.getWorkspaceCode(), flow.getCode(), e);
                        } finally {
                            MDC.remove(Constant.FLOW_CODE);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("数据流心跳异常", e);
            } finally {
                MDC.clear();
            }
        };
        // 定时发送心跳，检查各个实例是否正常运行
        this.heartbeatExecutor.scheduleAtFixedRate(runnable, 10,
                // 10秒检查一次
                10, TimeUnit.SECONDS);
    }

    /**
     * 启动流程
     *
     * @param workspaceCode 工作空间编码
     * @param code          流程编码
     */
    public void start(String workspaceCode, String code) {
        Flow flow = this.getFlow(workspaceCode, code);
        if (flow == null) {
            log.error("流程:{} 不存在", code);
            return;
        }
        try {
            // 启动
            flow.start();
        } catch (Exception e) {
            // 有可能部分组件已经启动，但是部分组件启动失败，这里调用一次stop全部终止掉
            // 例如启动mq监听时如果遇到java.net.SocketTimeoutException: Connect timed out，则会导致流程无法启动
            flow.stop();
            throw e;
        }
    }

    /**
     * 获取所有数据流
     *
     * @return 所有数据流
     */
    public List<Flow> getAllFlows() {
        if (CollUtil.isEmpty(this.flows)) {
            return Collections.emptyList();
        }
        List<Flow> allFlows = new ArrayList<>();
        for (Map<String, Flow> flowMap : this.flows.values()) {
            allFlows.addAll(flowMap.values());
        }
        return allFlows;
    }

}
