package cn.dataplatform.open.common.source;


import cn.dataplatform.open.common.annotation.Mask;
import cn.dataplatform.open.common.enums.DataSourceType;
import cn.dataplatform.open.common.enums.MaskType;
import cn.dataplatform.open.common.vo.flow.KeyValue;
import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.Node;

import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * KafkaDataSource
 *
 * @author dqw
 * @since 1.0.0
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
public class KafkaDataSource extends Source {

    private List<KeyValue> properties;

    private volatile AdminClient adminClient;


    @Override
    public DataSourceType type() {
        return DataSourceType.KAFKA;
    }

    /**
     * 获取管理客户端
     *
     * @return 管理客户端
     */
    public AdminClient getAdminClient() {
        if (this.adminClient == null) {
            synchronized (this) {
                if (this.adminClient == null) {
                    Properties props = new Properties();
                    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, this.url);
                    // 设置 SASL 认证相关配置
                    if (StrUtil.isNotBlank(this.username) && StrUtil.isNotBlank(this.password)) {
                        props.put("security.protocol", "SASL_SSL");
                        props.put("sasl.mechanism", "PLAIN");
                        props.put("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required " +
                                "username=\"" + this.username + "\" " +
                                "password=\"" + this.password + "\";");
                    }
                    // 添加额外的自定义属性
                    if (this.properties != null) {
                        for (KeyValue keyValue : this.properties) {
                            props.put(keyValue.getKey(), keyValue.getValue());
                        }
                    }
                    this.adminClient = AdminClient.create(props);
                }
            }
        }
        return this.adminClient;
    }

    /**
     * 健康检查
     *
     * @return true健康
     */
    @Override
    public Boolean health() throws Exception {
        // 设置描述集群的选项
        DescribeClusterOptions options = new DescribeClusterOptions().timeoutMs(5000);
        DescribeClusterResult result = this.getAdminClient().describeCluster(options);
        // 获取集群ID（验证连接）
        String clusterId = result.clusterId().get(5, TimeUnit.SECONDS);
        // 获取节点信息（验证至少有一个broker可用）
        Collection<Node> nodes = result.nodes().get(5, TimeUnit.SECONDS);
        return !nodes.isEmpty() && clusterId != null;
    }

    /**
     * 关闭
     */
    @Override
    public void close() {
        log.info("关闭Kafka数据源:" + this.code);
        if (this.adminClient != null) {
            this.adminClient.close();
            this.adminClient = null;
        }
    }

}