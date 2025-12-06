package cn.dataplatform.open.flow.service.core.exception;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/11/29
 * @since 1.0.0
 */
public class FlowRunException extends RuntimeException {

    public FlowRunException(String message, Throwable rootCause) {
        super(message, rootCause);
    }

    public FlowRunException(String message) {
        super(message);
    }

    public FlowRunException(Exception e) {
        super(e);
    }

}
