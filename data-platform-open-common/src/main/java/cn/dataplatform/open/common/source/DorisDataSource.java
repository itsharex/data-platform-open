package cn.dataplatform.open.common.source;

import cn.dataplatform.open.common.enums.DataSourceType;
import cn.hutool.core.io.IoUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DorisDataSource
 *
 * @author dqw
 * @since 1.0.0
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class DorisDataSource extends JDBCSource {

    private static final Pattern HOSTNAME = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?(?:/.*)?");
    private static final String DEFAULT_HOSTNAME = "localhost";


    private List<String> beNodes;
    private List<String> feNodes;

    /**
     * 获取数据源
     *
     * @return DataSource
     */
    @Override
    public DataSource getDataSource() {
        // 如果数据源为空则初始化
        if (this.dataSource == null) {
            synchronized (this) {
                if (this.dataSource == null) {
                    log.info("初始化Doris数据源:" + this.url);
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(this.url);
                    config.setUsername(this.username);
                    config.setPassword(this.password);
                    config.setDriverClassName(this.driverClassName);
                    // 连接超时时间
                    config.setConnectionTimeout(120_000);
                    // 空闲连接超时时间
                    config.setIdleTimeout(120_000);
                    // 最小空闲连接数
                    config.setMinimumIdle(5);
                    // 最大连接数
                    config.setMaximumPoolSize(this.maxPoolSize);
                    // 初始化失败超时时间，-1表示无限重试
                    config.setInitializationFailTimeout(-1);
                    // 每隔30秒发送keepalive
                    config.setKeepaliveTime(30000);
                    this.dataSource = new HikariDataSource(config);
                    log.info("初始化Doris数据源完成");
                }
            }
        }
        return this.dataSource;
    }

    /**
     * 数据源类型
     *
     * @return 类型
     */
    @Override
    public DataSourceType type() {
        return DataSourceType.DORIS;
    }

    /**
     * 健康检查
     *
     * @return true健康
     */
    @Override
    public Boolean health() throws Exception {
        Connection connection = null;
        try {
            // 加载数据库驱动
            Class.forName(this.driverClassName);
            // 尝试建立数据库连接
            connection = DriverManager.getConnection(this.url, this.username, this.password);
            // 如果连接成功,说明数据库健康
            return connection.isValid(3000);
        } finally {
            // 关闭数据库连接
            IoUtil.close(connection);
        }
    }


    /**
     * 从JDBC URL中提取主机名
     *
     * @return 主机名，如果无法提取则返回null
     */
    public String getHostname() {
        Matcher matcher = HOSTNAME.matcher(this.url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return DEFAULT_HOSTNAME;
    }

}