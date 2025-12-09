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
 * @author DaoDao
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class PostgreSQLDataSource extends JDBCSource {

    private static final Pattern PORT = Pattern.compile("jdbc:postgresql://.*:(\\d+)(/.*)?");
    private static final Pattern HOSTNAME = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?(?:/.*)?");
    private static final Pattern DBNAME = Pattern.compile("jdbc:postgresql://[^/]+/([^?]+)");

    private static final int DEFAULT_PORT = 5432;
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
     * 从PostgreSQL JDBC URL中提取端口号
     *
     * @return 端口号，如果未指定则返回默认端口5432
     */
    public Integer getPort() {
        Matcher matcher = PORT.matcher(this.url);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return DEFAULT_PORT;
    }

    /**
     * 从PostgreSQL JDBC URL中提取主机名
     *
     * @return 主机名，如果无法提取则返回localhost
     */
    public String getHostname() {
        Matcher matcher = HOSTNAME.matcher(this.url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return DEFAULT_HOSTNAME;
    }


    /**
     * 从PostgreSQL JDBC URL中提取数据库名称
     *
     * @return 数据库名称
     * @throws IllegalArgumentException 如果无法从URL中提取数据库名称
     */
    public String getDatabaseName() {
        Matcher matcher = DBNAME.matcher(this.url);
        if (matcher.find()) {
            String dbName = matcher.group(1);
            // 处理可能存在的参数部分
            int paramIndex = dbName.indexOf('?');
            if (paramIndex > 0) {
                dbName = dbName.substring(0, paramIndex);
            }
            return dbName;
        }
        throw new IllegalArgumentException("无法从JDBC URL中提取PostgreSQL数据库名称: " + this.url);
    }

}