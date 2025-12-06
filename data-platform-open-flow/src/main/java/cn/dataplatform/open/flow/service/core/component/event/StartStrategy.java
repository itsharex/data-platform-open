package cn.dataplatform.open.flow.service.core.component.event;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/8
 * @since 1.0.0
 */
public enum StartStrategy {

    /**
     * 增量
     */
    INCREASE,
    /**
     * 存量以及增量
     * <p>
     * 重启后则会重复执行之前的存量以及增量数据
     */
    STOCK_AND_INCREASE,
    /**
     * 从最新的一次保存点启动,如果没有,则报错
     */
    LAST_STORAGE,
    /**
     * 从最新的一次保存点启动,如果没有,则按照存量以及增量启动
     * <p>
     * 默认，建议使用此方式
     */
    AUTO,
    /**
     * 从指定保存点启动,没有则报错
     * <p>
     * 启动后建议修改为AUTO模式等，防止服务器重启后，依然从最开始自定义的保存点开始执行
     */
    CUSTOM,
    /**
     * 恢复模式
     */
    RECOVERY

}
