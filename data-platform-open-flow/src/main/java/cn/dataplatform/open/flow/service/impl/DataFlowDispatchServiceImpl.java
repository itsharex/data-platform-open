package cn.dataplatform.open.flow.service.impl;

import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.enums.RedisKey;
import cn.dataplatform.open.common.enums.ServerName;
import cn.dataplatform.open.common.server.Server;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.flow.core.Flow;
import cn.dataplatform.open.flow.core.FlowEngine;
import cn.dataplatform.open.flow.core.monitor.FlowMonitor;
import cn.dataplatform.open.flow.service.DataFlowDispatchService;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.thread.ThreadUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;


import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/25
 * @since 1.0.0
 */
@DependsOn("redisson")
@Order(20) // 待数据流加载完后开始启动调度线程
@Slf4j
@Service
public class DataFlowDispatchServiceImpl implements DataFlowDispatchService, ApplicationListener<ServletWebServerInitializedEvent>,
        DisposableBean {

    /**
     * 当前服务器启动后首次调度标识，如果整体集群重启过程中，
     * 导致节点数丢失引发调度，启动后还需要等待3分钟的问题
     */
    private volatile boolean firstDispatch = true;

    private Thread leaderThread;

    /**
     * 数据路调度间隔配置,单位毫秒
     */
    private int dispatchInterval;

    /**
     * 如果cpu内存使用率超过n%，则不调度数据流，阈值配置
     * <p>
     * 默认超过90%不再调度
     */
    @Value("${dp.flow.dispatch.resource-threshold:90}")
    private int resourceThreshold;

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private ServerManager serverManager;
    @Resource
    private FlowEngine flowEngine;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;
    @Resource
    private FlowMonitor flowMonitor;

    /**
     * 数据路调度间隔配置,单位毫秒
     *
     * @param dispatchInterval 调度间隔
     */
    @Value("${dp.flow.dispatch.interval:10000}")
    public void setDispatchInterval(int dispatchInterval) {
        // 最小值为5秒，否则抛出异常
        if (dispatchInterval < 5_000) {
            throw new IllegalArgumentException("调度间隔不能小于5秒");
        }
        this.dispatchInterval = dispatchInterval;
    }

    /**
     * 启动调度线程
     *
     * @param event 事件
     */
    @Override
    public void onApplicationEvent(@NonNull ServletWebServerInitializedEvent event) {
        this.leaderThread = new Thread(() -> {
            // 等待60秒，确保服务内部组件以及其他服务全部加载完毕
            ThreadUtil.sleep(60, TimeUnit.SECONDS);
            // destroy 方法会将leaderThread设置为null，表示停止调度线程
            while (this.leaderThread != null) {
                RLock lock = null;
                try {
                    lock = this.redissonClient.getLock(RedisKey.FLOW_DISPATCH_LEADER_LOCK.getKey());
                    // 尝试获取锁
                    if (lock.tryLock()) {
                        log.info("获取调度权,当前实例:{}", this.serverManager.instanceId());
                        // 作为leader，需要定期续期锁
                        while (!Thread.currentThread().isInterrupted()
                                // a实例拿到锁，进入循环开始执行，其他3个实例每隔ns拿锁判断是否有运行中的调度master
                                // 但是a实例因为redis故障导致无法正常续期，但是里面的循环还在执行，然后其他实例拿到锁开始执行
                                // 这里每次调度钱都会检测当前线程是否还持有锁，否则让出调度权
                                && lock.isLocked()
                                && lock.isHeldByCurrentThread()) {
                            // 每次使用新的请求ID，避免日志冲突
                            MDC.put(Constant.REQUEST_ID, UUID.randomUUID().toString(true));
                            // 每隔n秒检测调度一次数据流
                            this.dispatch();
                            // 首次调度标识作废
                            this.firstDispatch = false;
                            // 休眠一段时间，等待下次调度
                            ThreadUtil.sleep(this.dispatchInterval);
                        }
                        // 跳出while 循环，有可能当前服务锁续期失败，当redis服务连接出现问题时
                        // 当前线程休眠
                        log.info("当前实例:{}让出调度权,等待下次获取调度权", this.serverManager.instanceId());
                        ThreadUtil.sleep(5000);
                    } else {
                        // 其他节点获取到锁进行调度，当前节点首次标识也设置为false
                        this.firstDispatch = false;
                        // 不是Leader，等待并重试，获取调度权
                        log.info("当前实例:{}未获取到调度权,尝试下次获取", this.serverManager.instanceId());
                        ThreadUtil.sleep(5000);
                    }
                } catch (Exception e) {
                    log.error("调度选举失败,当前实例:{},错误:{}", this.serverManager.instanceId(), e.getMessage(), e);
                } finally {
                    if (lock != null) {
                        try {
                            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                                lock.unlock();
                                log.info("释放调度权:" + this.serverManager.instanceId());
                            }
                        } catch (Exception e) {
                            log.error("释放调度权失败,当前实例:{}", this.serverManager.instanceId(), e);
                        }
                    }
                    MDC.clear();
                }
            }
        });
        this.leaderThread.start();
    }

    /**
     * 调度数据流
     */
    @Override
    public void dispatch() {
        log.info("调度数据流,当前实例:{}", this.serverManager.instanceId());
        List<Flow> allFlows = this.flowEngine.getAllFlows();
        if (allFlows.isEmpty()) {
            log.info("没有数据流需要调度");
            return;
        }
        // 获取可用的服务实例
        Collection<Server> servers = this.serverManager.availableList(ServerName.FLOW_SERVER.getValue());
        // 待公布
    }

    /**
     * 监控 debezium 等组件是否正常,如果异常，则重启
     * <p>
     * 当运行数据流的某个节点死了,或者被缩容到，其他节点接力
     *
     * @param flow 数据流
     */
    private void detectionComponentOnly(Flow flow) {
    }


    /**
     * 停止调度线程
     */
    @Override
    public void destroy() {
        if (this.leaderThread != null) {
            this.leaderThread.interrupt();
            this.leaderThread = null;
        }
    }

}
