package cn.dataplatform.open.flow.util;

import cn.dataplatform.open.flow.core.pack.Column;
import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/13
 * @since 1.0.0
 */
public class DatabaseMetaDataUtils {

    public static final String COLUMN_NAME = "COLUMN_NAME";
    public static final String DATA_TYPE = "DATA_TYPE";
    public static final String TYPE_NAME = "TYPE_NAME";

    /**
     * 存储表列结构
     */
    private static final Cache<String, List<Column>> TABLE_SCHEMA_CACHE = CacheUtil.newLRUCache(1000,
            60 * 1000);


    /**
     * 获取表列信息（带缓存）
     *
     * @param connection     连接
     * @param datasourceCode 数据源编码
     * @param schema         数据库名
     * @param table          表名
     * @return 列名列表
     */
    @SneakyThrows
    public static List<Column> getTableColumns(Connection connection, String datasourceCode,
                                               String schema, String table) {
        String cacheKey = datasourceCode + "." + schema + "." + table;
        // 先从缓存获取
        List<Column> cachedColumns = TABLE_SCHEMA_CACHE.get(cacheKey);
        if (cachedColumns != null) {
            return cachedColumns;
        }
        // 缓存中没有，查询数据库
        List<Column> tableColumns = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, schema, table, null)) {
            while (columns.next()) {
                String columnName = columns.getString(COLUMN_NAME);
                int columnType = columns.getInt(DATA_TYPE);
                String columnTypeName = columns.getString(TYPE_NAME);
                tableColumns.add(new Column(columnName, columnType, columnTypeName));
            }
        }
        TABLE_SCHEMA_CACHE.put(cacheKey, tableColumns);
        return tableColumns;
    }

}
