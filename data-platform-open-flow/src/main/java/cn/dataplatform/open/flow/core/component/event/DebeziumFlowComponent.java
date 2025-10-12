package cn.dataplatform.open.flow.core.component.event;

import cn.dataplatform.open.common.enums.Status;
import cn.dataplatform.open.flow.core.Context;
import cn.dataplatform.open.flow.core.Flow;
import cn.dataplatform.open.flow.core.Transmit;
import cn.dataplatform.open.flow.core.annotation.ExcludeMonitor;
import cn.dataplatform.open.flow.core.component.FlowComponent;
import io.debezium.data.Envelope;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/10/13
 * @since 1.0.0
 */
@ExcludeMonitor
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class DebeziumFlowComponent extends FlowComponent {

    @NotBlank
    private String datasourceCode;
    /**
     * 逗号隔开
     */
    @NotBlank
    private String schemas;
    /**
     * 逗号隔开
     * <p>
     * schema.table
     */
    private String tables;
    /**
     * 状态
     */
    private Status status = Status.ENABLE;
    /**
     * 自定义配置信息
     */
    private Properties properties;

    /**
     * 监听的操作类型
     */
    private List<Envelope.Operation> operations = Arrays.asList(Envelope.Operation.READ,
            Envelope.Operation.CREATE,
            Envelope.Operation.UPDATE,
            Envelope.Operation.DELETE);

    /**
     * 如果没有指定,则自己随机生成一个,从5400-6400中随机分配一个数字
     */
    private Integer databaseServerId;


    public DebeziumFlowComponent(Flow flow, String code) {
        super(flow, code);
    }

    @Override
    public void run(Transmit transmit, Context context) {

    }

}
