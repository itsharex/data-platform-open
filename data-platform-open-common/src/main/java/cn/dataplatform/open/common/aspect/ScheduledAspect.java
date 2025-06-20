package cn.dataplatform.open.common.aspect;

import cn.dataplatform.open.common.annotation.ScheduledGlobalLock;
import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.enums.RedisKey;
import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/22
 * @since 1.0.0
 */
@Aspect
@Component
public class ScheduledAspect {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 拦截所有带有 @Scheduled 注解的方法 执行时增加requestId 以及判断是否需要分布式锁
     *
     * @param joinPoint j
     * @return r
     * @throws Throwable t
     */
    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MDC.put(Constant.REQUEST_ID, UUID.fastUUID().toString(true));
        // 1. 检查方法是否有 @ScheduledGlobalLock 注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        ScheduledGlobalLock scheduledGlobalLock = method.getAnnotation(ScheduledGlobalLock.class);
        RLock lock = null;
        try {
            if (scheduledGlobalLock != null) {
                String methodName = method.getName();
                lock = this.redissonClient.getLock(RedisKey.SCHEDULED_LOCK.build(methodName));
                if (!lock.tryLock(scheduledGlobalLock.waitTime(),
                        scheduledGlobalLock.leaseTime(), scheduledGlobalLock.unit())) {
                    return null; // 获取锁失败，直接返回
                }
            }
            // 执行定时任务
            return joinPoint.proceed();
        } finally {
            // 如果有锁，并且当前线程持有锁，则释放
            if (lock != null && lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            MDC.clear();
        }
    }

}

