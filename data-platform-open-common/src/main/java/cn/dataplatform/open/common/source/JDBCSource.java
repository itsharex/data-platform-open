package cn.dataplatform.open.common.source;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.driver.yaml.YamlJDBCConfiguration;
import org.apache.shardingsphere.infra.config.mode.ModeConfiguration;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.infra.database.core.DefaultDatabase;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.apache.shardingsphere.infra.yaml.config.swapper.mode.YamlModeConfigurationSwapper;
import org.apache.shardingsphere.infra.yaml.config.swapper.rule.YamlRuleConfigurationSwapperEngine;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/5/17
 * @since 1.0.0
 */
@Getter
@Setter
@Slf4j
public abstract class JDBCSource extends Source {

    public static final ColumnMapRowMapper COLUMN_MAP_ROW_MAPPER = new ColumnMapRowMapper();

    @NotBlank
    protected String driverClassName;
    /**
     * 最大连接数
     */
    @Min(value = 1)
    protected Integer maxPoolSize = 10;
    private String partitioningAlgorithm;
    /**
     * 数据源
     */
    @Setter(AccessLevel.NONE)
    protected volatile DataSource dataSource;
    @Setter(AccessLevel.NONE)
    protected volatile JdbcClient jdbcClient;

    /**
     * 获取JdbcClient
     *
     * @return JdbcClient
     */
    public JdbcClient getJdbcClient() {
        if (this.jdbcClient == null) {
            synchronized (this) {
                if (this.jdbcClient == null) {
                    log.info("初始化JdbcClient:" + this.url);
                    this.jdbcClient = JdbcClient.create(this.getDataSource());
                    log.info("初始化JdbcClient完成");
                }
            }
        }
        return this.jdbcClient;
    }

    /**
     * 获取数据源
     *
     * @return 数据源
     */
    public DataSource getDataSource() {
        if (this.dataSource == null) {
            synchronized (this) {
                if (this.dataSource == null) {
                    log.info("初始化JDBC数据源:" + this.url);
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(this.url);
                    config.setUsername(this.username);
                    config.setPassword(this.password);
                    config.setDriverClassName(this.driverClassName);
                    // 最小空闲连接数
                    config.setMinimumIdle(5);
                    // 最大连接数
                    config.setMaximumPoolSize(this.maxPoolSize);
                    // 空闲连接超时时间
                    config.setIdleTimeout(30000);
                    // 连接超时时间
                    config.setConnectionTimeout(30000);
                    // 初始化失败超时时间，-1表示无限重试
                    config.setInitializationFailTimeout(-1);
                    // 每隔30秒发送keepalive
                    config.setKeepaliveTime(30000);
                    HikariDataSource hikariDataSource = new HikariDataSource(config);
                    this.dataSource = this.createShardingDataSource(this.partitioningAlgorithm, hikariDataSource);
                    log.info("初始化JDBC数据源完成");
                }
            }
        }
        return this.dataSource;
    }

    /**
     * 获取连接
     *
     * @return 连接
     */
    public Connection getConnection() {
        return this.getConnection(true);
    }

    /**
     * 获取连接
     *
     * @param autoCommit 是否自动提交
     * @return 连接
     */
    @SneakyThrows
    public Connection getConnection(boolean autoCommit) {
        // 如果数据源为空则初始化
        DataSource dataSource = this.getDataSource();
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(autoCommit);
        return connection;
    }

    /**
     * 创建分片数据源
     *
     * @param partitioningAlgorithm 分表规则
     * @param hikariDataSource      原始数据源
     * @return 分片数据源
     */
    public DataSource createShardingDataSource(String partitioningAlgorithm, HikariDataSource hikariDataSource) {
        if (StrUtil.isBlank(partitioningAlgorithm)) {
            // 普通数据源
            return hikariDataSource;
        }
        // @see https://shardingsphere.apache.org/document/current/cn/user-manual/shardingsphere-jdbc/yaml-config/rules/sharding/
        log.info("初始化分表规则:" + partitioningAlgorithm);
        try {
            YamlJDBCConfiguration jdbcConfig = YamlEngine.unmarshal(partitioningAlgorithm, YamlJDBCConfiguration.class);
            String databaseName = jdbcConfig.getDatabaseName();
            Map<String, DataSource> dataSourceMap = new LinkedHashMap<>(1);
            dataSourceMap.put(Objects.requireNonNullElse(databaseName, DefaultDatabase.LOGIC_NAME), hikariDataSource);
            return this.createDataSource(dataSourceMap, jdbcConfig);
        } catch (Exception e) {
            throw new RuntimeException("初始化分表规则失败", e);
        }
    }

    /**
     * 创建Sharding数据源代理
     *
     * @param dataSourceMap 数据源
     * @param jdbcConfig    配置
     * @return 数据源
     */
    public DataSource createDataSource(final Map<String, DataSource> dataSourceMap, final YamlJDBCConfiguration jdbcConfig)
            throws SQLException {
        ModeConfiguration modeConfig = null == jdbcConfig.getMode() ? null :
                new YamlModeConfigurationSwapper().swapToObject(jdbcConfig.getMode());
        jdbcConfig.rebuild();
        Collection<RuleConfiguration> ruleConfigs = new YamlRuleConfigurationSwapperEngine().
                swapToRuleConfigurations(jdbcConfig.getRules());
        return ShardingSphereDataSourceFactory.createDataSource(jdbcConfig.getDatabaseName(),
                modeConfig, dataSourceMap, ruleConfigs, jdbcConfig.getProps());
    }

    /**
     * 关闭数据源
     */
    @Override
    public void close() {
        if (this.dataSource != null) {
            if (this.dataSource instanceof AutoCloseable closeable) {
                IoUtil.close(closeable);
            }
            this.dataSource = null;
            this.jdbcClient = null;
        }
    }

}
