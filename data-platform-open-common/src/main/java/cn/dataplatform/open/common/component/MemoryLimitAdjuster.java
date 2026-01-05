package cn.dataplatform.open.common.component;

import lombok.extern.slf4j.Slf4j;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2026/1/5
 * @since 1.0.0
 */
@Slf4j
public class MemoryLimitAdjuster implements LimitAdjuster {

    /**
     * 内存限制器
     *
     * @return 限制数量
     */
    @Override
    public long limit() {
        Runtime runtime = Runtime.getRuntime();
        // -Xmx 设置的最大上限
        long maxMemory = runtime.maxMemory();
        // 当前 JVM 已从操作系统申请到的内存
        long totalMemory = runtime.totalMemory();
        // 当前已申请内存中的空闲部分
        long freeMemory = runtime.freeMemory();
        // 计算真实已用内存
        long usedMemory = totalMemory - freeMemory;
        if (maxMemory == Long.MAX_VALUE) {
            // 如果无上限，视当前 total 为上限
            maxMemory = totalMemory;
        }
        double memoryUsageRatio = (double) usedMemory / maxMemory * 100;
        long maxMemoryMB = maxMemory / (1024 * 1024);
        long usedMemoryMB = usedMemory / (1024 * 1024);
        log.info("当前内存上限:{}MB,当前分配:{}MB,已用:{}MB,使用率:{}%", maxMemoryMB,
                totalMemory / (1024 * 1024), usedMemoryMB, String.format("%.2f", memoryUsageRatio));
        // 硬件基准限制，防止服务器配置过低
        long finalSize = this.getFinalSize(maxMemoryMB, memoryUsageRatio);
        log.info("根据内存使用情况,限制条目为: {}", finalSize);
        return finalSize;
    }

    /**
     * 计算最终查询条目
     *
     * @param maxMemoryMB      最大内存 MB
     * @param memoryUsageRatio 内存使用率
     * @return 最终查询条目
     */
    private long getFinalSize(long maxMemoryMB, double memoryUsageRatio) {
        long hardwareBaseLimit;
        if (maxMemoryMB <= 512) {
            // 512MB 机器最多只敢查 1000 条
            hardwareBaseLimit = 1000;
        } else if (maxMemoryMB <= 1024) {
            // 1G 机器最多查 3000 条
            hardwareBaseLimit = 3000;
        } else {
            // 大内存机器上限高
            hardwareBaseLimit = 20000;
        }
        // 动态安全水位 随着内存被填满，逐渐减少处理量，防止 OOM
        long safetyThrottleLimit;
        if (memoryUsageRatio >= 90) {
            // 极度危险区域，降级保护
            safetyThrottleLimit = 500;
        } else if (memoryUsageRatio >= 80) {
            safetyThrottleLimit = 1000;
        } else if (memoryUsageRatio >= 70) {
            safetyThrottleLimit = 3000;
        } else if (memoryUsageRatio >= 60) {
            safetyThrottleLimit = 5000;
        } else if (memoryUsageRatio >= 50) {
            safetyThrottleLimit = 10000;
        } else {
            // 内存非常健康
            safetyThrottleLimit = 20000;
        }
        // 既不能超过硬件能力的上限，也不能忽视当前内存的压力
        return Math.min(hardwareBaseLimit, safetyThrottleLimit);
    }

}
