package cn.dataplatform.open.flow.core.component;

import cn.dataplatform.open.common.enums.RedisKey;
import cn.dataplatform.open.common.enums.Status;
import cn.dataplatform.open.flow.core.Context;
import cn.dataplatform.open.flow.core.Flow;
import cn.dataplatform.open.flow.core.Transmit;
import cn.dataplatform.open.flow.core.annotation.Retryable;
import cn.dataplatform.open.flow.core.record.Record;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateLimiterConfig;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/7/13
 * @since 1.0.0
 */
@Slf4j
@Getter
@Setter
@Retryable
public class RateLimitFlowComponent extends FlowComponent {

    /**
     * 每个周期内请求次数
     */
    private Long limit;

    /**
     * 周期时间内触发
     */
    private Long refreshInterval;

    /**
     * 时间单位
     */
    private ChronoUnit chronoUnit;

    /**
     * 状态
     */
    private Status status = Status.ENABLE;

    /**
     * 限流器
     */
    @Setter(AccessLevel.NONE)
    private volatile RRateLimiter rateLimiter;

    /**
     * 构造方法
     *
     * @param flow 流程
     * @param code 当前组件
     */
    public RateLimitFlowComponent(Flow flow, String code) {
        super(flow, code);
    }

    /**
     * 例如需要限流的场景使用
     *
     * @param transmit 传输对象
     * @param context  上下文
     */
    @Override
    public void run(Transmit transmit, Context context) {
        Record record = transmit.getRecord();
        if (record.isEmpty()) {
            return;
        }
        if (Objects.equals(this.status, Status.ENABLE)) {
            if (this.rateLimiter == null) {
                synchronized (this) {
                    if (this.rateLimiter == null) {
                        RedissonClient redissonClient = this.getApplicationContext().getBean(RedissonClient.class);
                        this.rateLimiter = redissonClient.getRateLimiter(RedisKey.FLOW_RATE_LIMIT.build(this.getKey()));
                        // 如果没有初始化，则初始化
                        if (!this.rateLimiter.isExists() || this.hasConfigChanged()) {
                            this.rateLimiter.trySetRate(RateType.OVERALL, this.limit, Duration.of(this.refreshInterval, this.chronoUnit));
                            log.info("限流组件[{}]初始化成功,限流速率:{},间隔：{} {}",
                                    this.getCode(), this.limit, this.refreshInterval, this.chronoUnit.name());
                        }
                    }
                }
            }
            // 一直等待获取令牌，直到获取到为止
            log.info("限流组件[{}]开始获取令牌,限流速率:{},刷新间隔:{} {}",
                    this.getKey(), this.limit, this.refreshInterval, this.chronoUnit.name());
            this.rateLimiter.acquire(1);
            log.info("限流组件[{}]获取令牌成功", this.getKey());
        }
        // 执行下游节点
        this.runNext(() -> {
            Transmit newTransmit = new Transmit();
            newTransmit.setFlowComponent(this);
            newTransmit.setRecord(record);
            return newTransmit;
        }, context);
    }

    /**
     * 检查限流配置是否发生变化，如果限流速率或间隔与预期不符，则需要重新配置
     *
     * @return 是否需要重新配置
     */
    private boolean hasConfigChanged() {
        RateLimiterConfig rateLimiterConfig = this.rateLimiter.getConfig();
        long currentRate = rateLimiterConfig.getRate();
        long currentInterval = rateLimiterConfig.getRateInterval();
        // 计算预期间隔（毫秒）
        long expectedInterval = Duration.of(this.refreshInterval, this.chronoUnit).toMillis();
        // 如果当前的限流速率或间隔与预期不符，则需要重新配置
        return currentRate != this.limit || currentInterval != expectedInterval;
    }


    /**
     * 停止组件
     */
    @Override
    public void stop() {
        if (this.rateLimiter != null) {
            this.rateLimiter.delete();
            log.info("限流组件[{}]已停止", this.getKey());
        }
    }

}
