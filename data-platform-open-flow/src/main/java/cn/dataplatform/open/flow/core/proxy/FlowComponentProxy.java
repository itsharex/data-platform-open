package cn.dataplatform.open.flow.core.proxy;

import cn.dataplatform.open.flow.core.Transmit;
import cn.dataplatform.open.flow.core.annotation.Retryable;
import cn.dataplatform.open.flow.core.component.FlowComponent;
import cn.dataplatform.open.flow.core.monitor.FlowComponentMonitor;
import cn.dataplatform.open.flow.core.pack.StopWatch;
import cn.hutool.extra.spring.SpringUtil;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 〈FlowComponentProxy〉
 *
 * @author dingqianwen
 * @date 2025/2/26 1:23
 * @since 1.0.0
 */
@Slf4j
public class FlowComponentProxy implements MethodInterceptor {
    /**
     * 组件run方法
     */
    private static final String RUN_METHOD = "run";

    private final FlowComponentMonitor flowComponentMonitor;
    private final FlowComponent flowComponent;

    /**
     * 重试
     */
    private RetryTemplate retryTemplate;

    /**
     * 构造方法
     *
     * @param flowComponent 组件
     */
    public FlowComponentProxy(FlowComponent flowComponent) {
        this.flowComponentMonitor = SpringUtil.getBean(FlowComponentMonitor.class);
        this.flowComponent = flowComponent;
        Retryable retryable = flowComponent.getClass().getAnnotation(Retryable.class);
        if (retryable != null) {
            // 创建 RetryTemplate 实例
            this.retryTemplate = new RetryTemplate();
            // 配置重试策略
            Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
            // 指定要重试的异常类型
            retryableExceptions.put(Exception.class, true);
            SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                    // 设置最大重试次数
                    retryable.maxAttempts(),
                    retryableExceptions
            );
            this.retryTemplate.setRetryPolicy(retryPolicy);
            // 配置重试间隔策略,使用指数退避策略,每次失败后重试间隔时间增加一倍
            ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
            // 设置初始重试间隔为 1000 毫秒
            backOffPolicy.setInitialInterval(retryable.initialInterval());
            // 设置乘数,每次重试间隔时间将乘以该乘数
            backOffPolicy.setMultiplier(retryable.multiplier());
            // 设置最大重试间隔,避免间隔时间过长 最多等待60秒
            backOffPolicy.setMaxInterval(retryable.maxInterval());
            this.retryTemplate.setBackOffPolicy(backOffPolicy);
        }
    }

    /**
     * 代理方法
     *
     * @param object      对象
     * @param method      方法
     * @param objects     参数
     * @param methodProxy 代理
     * @return 结果
     * @throws Throwable 异常
     */
    @Override
    public Object intercept(Object object, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        // 特殊处理toString
        if ("toString".equals(method.getName())) {
            return "Proxy@" + System.identityHashCode(this) + "->" + this.flowComponent.toString();
        }
        // 调用run方法时,统计run方法执行数量,执行耗时,重试时也算,失败时记录异常次数即可
        if (RUN_METHOD.equals(method.getName())) {
            // 调用时 arg1-Transmit arg2-Context
            Transmit transmit = (Transmit) objects[0];
            // 获取组件的唯一key
            String flowComponentKey = this.flowComponent.getKey();
            FlowComponent parentFlowComponent = transmit == null ? null : transmit.getFlowComponent();
            // 执行run方法
            try {
                StopWatch stopWatch = null;
                if (parentFlowComponent != null) {
                    // 开始计时
                    stopWatch = new StopWatch();
                    stopWatch.start();
                    transmit.setTimer(stopWatch);
                    int size = transmit.getRecord().size();
                    this.flowComponentMonitor.processNumber(parentFlowComponent, this.flowComponent, size);
                }
                Object execute;
                if (this.retryTemplate != null) {
                    execute = this.retryTemplate.execute(context -> {
                        int retryCount = context.getRetryCount();
                        if (retryCount > 0) {
                            // 重试次数大于0时,打印日志
                            log.warn("组件[{}]执行失败,开始第[{}]次重试", flowComponentKey, retryCount, context.getLastThrowable());
                        }
                        // 开始执行,支持重试最多等待60000秒
                        return methodProxy.invoke(this.flowComponent, objects);
                    });
                } else {
                    // 不需要重试
                    execute = methodProxy.invoke(this.flowComponent, objects);
                }
                // 记录耗时
                if (stopWatch != null) {
                    long totalTimeMillis = stopWatch.getTotalTimeMillis();
                    Timer timer = this.flowComponentMonitor.runTimer(parentFlowComponent, this.flowComponent);
                    timer.record(totalTimeMillis, TimeUnit.MILLISECONDS);
                }
                return execute;
            } catch (Throwable e) {
                if (parentFlowComponent != null) {
                    this.flowComponentMonitor.runError(parentFlowComponent, this.flowComponent);
                }
                throw e;
            }
        }
        // 其他方法
        return methodProxy.invoke(this.flowComponent, objects);
    }

}