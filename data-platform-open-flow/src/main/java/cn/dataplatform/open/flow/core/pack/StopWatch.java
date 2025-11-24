package cn.dataplatform.open.flow.core.pack;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/11/24
 * @since 1.0.0
 */
public class StopWatch {

    /**
     * 总计时毫秒数
     */
    private Long totalTimeMillis = 0L;
    /**
     * 开始时间毫秒数
     */
    private Long startTimeMillis;

    public StopWatch() {
    }

    /**
     * 开始计时
     */
    public void start() {
        if (this.startTimeMillis == null) {
            this.startTimeMillis = System.currentTimeMillis();
        } else {
            throw new IllegalStateException("StopWatch is already running");
        }
    }

    /**
     * 停止计时
     */
    public void stop() {
        if (this.startTimeMillis == null) {
            throw new IllegalStateException("StopWatch is not running");
        }
        long endTimeMillis = System.currentTimeMillis();
        this.totalTimeMillis += (endTimeMillis - this.startTimeMillis);
        this.startTimeMillis = null;
    }

    /**
     * 计时器状态是否运行
     *
     * @return 计时器状态是否运行
     */
    public boolean isRunning() {
        return this.startTimeMillis != null;
    }

    /**
     * 获取总计时毫秒数
     *
     * @return 总计时毫秒数
     */
    public long getTotalTimeMillis() {
        if (this.startTimeMillis != null) {
            long currentTimeMillis = System.currentTimeMillis();
            return this.totalTimeMillis + (currentTimeMillis - this.startTimeMillis);
        }
        return this.totalTimeMillis;
    }

}
