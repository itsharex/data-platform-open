package cn.dataplatform.open.flow.core.component.event.connector;


import cn.dataplatform.open.common.source.Source;
import cn.dataplatform.open.flow.core.component.event.DebeziumFlowComponent;
import io.debezium.config.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/27
 * @since 1.0.0
 */
@Slf4j
public class PostgreSQLConnectorConfigure implements ConnectorConfigure {

    /**
     * 配置Debezium连接器
     *
     * @param debeziumFlowComponent Debezium流组件
     * @param source                数据源
     * @param builder               配置构建器
     */
    @Override
    public void configure(DebeziumFlowComponent debeziumFlowComponent, Source source, Configuration.Builder builder) {
        // ...
    }

}
