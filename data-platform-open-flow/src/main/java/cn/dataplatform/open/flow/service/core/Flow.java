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
package cn.dataplatform.open.flow.service.core;

import cn.dataplatform.open.common.enums.flow.DataFlowRunStrategy;
import cn.dataplatform.open.flow.service.core.component.FlowComponent;
import cn.dataplatform.open.flow.service.core.proxy.FlowComponentProxy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.proxy.Enhancer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/1/4
 * @since 1.0.0
 */
@Slf4j
@Data
public class Flow {

    private Long id;

    private String workspaceCode;

    private String code;

    private String name;

    private String version;

    /**
     * 打印更详细的日志，但是会降低性能，以及增加日志存储成本，前期调试阶段可以开启，后续可以关闭
     */
    private boolean debug = false;
    /**
     * 添加到引擎中的时间
     */
    private LocalDateTime addTime;

    /**
     * 是否启用告警
     */
    private boolean enableAlarm;

    /**
     * 是否启用监控
     */
    private boolean enableMonitor;

    /**
     * 运行策略
     */
    private DataFlowRunStrategy runStrategy;

    /**
     * 运行实例数量
     */
    private Integer instanceNumber;

    /**
     * 指定实例
     */
    private List<String> specifyInstances;

    /**
     * 流程是否运行中
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 流程节点
     */
    private Map<String, FlowComponent> flowComponents = new ConcurrentHashMap<>();


    /**
     * 添加组件,并注册代理
     *
     * @param flowComponent 组件
     * @return 代理类
     */
    public FlowComponent addFlowComponent(FlowComponent flowComponent) {
        // 代理flowComponent用来做监控等
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(flowComponent.getClass());
        enhancer.setCallback(new FlowComponentProxy(flowComponent));
        FlowComponent proxy = (FlowComponent) enhancer.create(
                new Class[]{this.getClass(), String.class},
                new Object[]{this, flowComponent.getCode()}
        );
        this.flowComponents.put(flowComponent.getCode(), proxy);
        return proxy;
    }

    /**
     * 是否运行中
     *
     * @return 是否运行中
     */
    public boolean isRunning() {
        return this.running.get();
    }

}