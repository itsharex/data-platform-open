package cn.dataplatform.open.common.source;

import cn.dataplatform.open.common.enums.DataSourceType;
import cn.hutool.core.io.IoUtil;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MySQLDataSource
 *
 * @author dqw
 * @since 1.0.0
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class MySQLDataSource extends JDBCSource {

    private static final Pattern PORT = java.util.regex.Pattern.compile("jdbc:mysql://.*:(\\d+)(/.*)?");
    private static final Pattern HOSTNAME = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?(?:/.*)?");
    private static final int DEFAULT_PORT = 3306;
    private static final String DEFAULT_HOSTNAME = "localhost";

    /**
     * 数据源类型
     *
     * @return 类型
     */
    @Override
    public DataSourceType type() {
        return DataSourceType.MYSQL;
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

    /**
     * 从MySQL JDBC URL中提取端口号
     *
     * @return 端口号，如果未指定则返回默认端口3306
     */
    public Integer getPort() {
        // 正则表达式匹配端口号
        Matcher matcher = PORT.matcher(this.url);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return DEFAULT_PORT;
    }

}