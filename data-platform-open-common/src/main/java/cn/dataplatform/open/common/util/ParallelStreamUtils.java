package cn.dataplatform.open.common.util;

import cn.dataplatform.open.common.exception.ParallelException;
import cn.hutool.core.collection.CollUtil;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/24
 * @since 1.0.0
 */
public class ParallelStreamUtils {

    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 执行并行流操作-使用虚拟线程
     *
     * @param components 组件列表
     * @param action     操作
     * @param <T>        组件类型
     */
    public static <T> void forEach(Collection<T> components, Consumer<T> action) {
        if (components == null) {
            return;
        }
        ParallelStreamUtils.forEach(components, action, true);
    }

    /**
     * 执行并行流操作
     *
     * @param components    组件列表
     * @param action        操作
     * @param <T>           组件类型
     * @param virtualThread 是否使用虚拟线程
     */
    public static <T> void forEach(Collection<T> components, Consumer<T> action, Boolean virtualThread) {
        if (CollUtil.isEmpty(components)) {
            return;
        }
        Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        if (virtualThread != null && virtualThread) {
            CompletableFuture<?>[] futures = components.stream()
                    .map(component -> CompletableFuture.runAsync(() -> {
                        try {
                            if (copyOfContextMap != null) {
                                MDC.setContextMap(copyOfContextMap); // 设置 MDC
                            }
                            action.accept(component);
                        } finally {
                            MDC.clear();
                        }
                    }, VIRTUAL_EXECUTOR))
                    .toArray(CompletableFuture[]::new);
            try {
                // 等待所有任务完成
                CompletableFuture.allOf(futures).join();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException r) {
                    throw r;
                }
                throw new ParallelException("并行处理失败", cause);
            }
        } else {
            try {
                components.parallelStream().forEach(component -> {
                    // 每次设置一个新的
                    if (copyOfContextMap != null) {
                        MDC.setContextMap(copyOfContextMap);
                    }
                    try {
                        action.accept(component);
                    } finally {
                        // 清理
                        MDC.clear();
                    }
                });
            } finally {
                // 如果有两个元素，使用parallelStream时，一个使用主线程，一个使用ForkJoinPool,
                // 可能会导致主线程的 MDC 被 ForkJoinPool 的线程清除，所以在 finally 中恢复主线程的 MDC
                if (copyOfContextMap != null) {
                    MDC.setContextMap(copyOfContextMap);
                }
            }
        }
    }

}