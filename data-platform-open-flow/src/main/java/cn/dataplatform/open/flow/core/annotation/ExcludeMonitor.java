package cn.dataplatform.open.flow.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 排除监控的组件，已经手动注册监控
 *
 * @author dingqianwen
 * @date 2025/5/22
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcludeMonitor {
}
