package cn.dataplatform.open.flow.service.core.exception;

/**
 * 数据流下游节点运行异常
 *
 * @author dingqianwen
 * @date 2025/6/27
 * @since 1.0.0
 */
public class FlowRunNextException extends RuntimeException {

    public FlowRunNextException(String message, Throwable rootCause) {
        super(message, rootCause);
    }

}
