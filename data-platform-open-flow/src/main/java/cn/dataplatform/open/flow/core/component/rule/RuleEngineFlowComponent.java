package cn.dataplatform.open.flow.core.component.rule;

import cn.dataplatform.open.flow.core.Context;
import cn.dataplatform.open.flow.core.Flow;
import cn.dataplatform.open.flow.core.Transmit;
import cn.dataplatform.open.flow.core.annotation.Retryable;
import cn.dataplatform.open.flow.core.component.FlowComponent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 〈RuleEngineFlowComponent〉
 *
 * @author dqw
 * @date 2025/1/10
 * @since 1.0.0
 */
@Slf4j
@Getter
@Setter
@Retryable
public class RuleEngineFlowComponent extends FlowComponent {

    /**
     * 构造方法
     *
     * @param flow 流程
     * @param code 当前组件
     */
    public RuleEngineFlowComponent(Flow flow, String code) {
        super(flow, code);
    }

    @Override
    public void run(Transmit transmit, Context context) {
        throw new UnsupportedOperationException("不支持的操作");
    }

}