package cn.dataplatform.open.common.vo.flow;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/3
 * @since 1.0.0
 */
@Data
public class FlowError implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ErrorType type;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;

    private String message;

    private String instanceId;

    /**
     * 错误类型
     */
    @AllArgsConstructor
    @Getter
    public enum ErrorType implements Serializable {

        /**
         * 启动失败,需要根据启动失败原因手动处理
         */
        STARTUP("STARTUP", "启动失败"),

        /**
         * 运行异常，但是仍然可以继续
         */
        RUNNING("RUNNING", "运行异常"),
        /**
         * 通用警告
         */
        WARNING("WARNING", "警告"),
        /**
         * 说明缺少节点，例如选择使用2个实例，但是只启动了1个实例
         */
        WARNING_101("WARNING", "数据流运行实例数量不足"),
        WARNING_102("WARNING", "数据流运行时指定的数据流实例不存在"),
        WARNING_103("WARNING", "所有候选实例CPU/内存使用率过高"),
        WARNING_104("WARNING", "部分实例CPU/内存使用率过高"),
        /**
         * 异常中断，数据流运行崩溃，从引擎中移除，等待服务器重启，或者下次发布
         */
        ABORT("ABORT", "异常中断"),
        ;
        @Serial
        private static final long serialVersionUID = 1L;

        private final String type;
        private final String name;
    }

}
