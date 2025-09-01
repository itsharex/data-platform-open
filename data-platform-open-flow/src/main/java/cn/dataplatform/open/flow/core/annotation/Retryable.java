package cn.dataplatform.open.flow.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 哪些组件需要重试，后续可以考虑页面配置，这里暂时注解方式
 *
 * @author dingqianwen
 * @date 2025/9/1
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {

    /**
     * 初始重试间隔，单位毫秒
     *
     * @return 初始重试间隔，单位毫秒
     */
    long initialInterval() default 1000;

    /**
     * 重试间隔倍数
     *
     * @return 重试间隔倍数
     */
    double multiplier() default 3.0;

    /**
     * 最大重试间隔，单位毫秒
     *
     * @return 最大重试间隔，单位毫秒
     */
    long maxInterval() default 60000;

    /**
     * 最大重试次数
     *
     * @return 最大重试次数
     */
    int maxAttempts() default 3;

}
