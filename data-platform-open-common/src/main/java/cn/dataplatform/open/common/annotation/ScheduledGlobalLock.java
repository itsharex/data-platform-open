package cn.dataplatform.open.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/8
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScheduledGlobalLock {

    /**
     * 获取锁的等待时间
     *
     * @return r
     */
    long waitTime() default 0L;

    /**
     * 获取锁的持有时间
     *
     * @return r
     */
    long leaseTime() default 60L;

    /**
     * 时间单位
     *
     * @return r
     */
    TimeUnit unit() default TimeUnit.SECONDS;

}
