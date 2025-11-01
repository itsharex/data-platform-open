package cn.dataplatform.open.web.service.datasource.tables;


import cn.dataplatform.open.common.enums.DataSourceType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/3/15
 * @since 1.0.0
 */
@Component
public class DataSourceTableManager implements ApplicationContextAware {

    private final Map<DataSourceType, DataSourceTable> map = new ConcurrentHashMap<>();


    /**
     * 设置应用上下文
     *
     * @param applicationContext 应用上下文
     * @throws BeansException 异常
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        Map<String, DataSourceTable> beansOfType = applicationContext.getBeansOfType(DataSourceTable.class);
        for (DataSourceTable dataSourceTable : beansOfType.values()) {
            DataSourceTable existing = this.map.put(dataSourceTable.getDataSourceType(), dataSourceTable);
            // 检查是否存在重复的处理器，防止写错
            if (existing != null) {
                throw new IllegalStateException("存在重复的数据源类型: " + dataSourceTable.getDataSourceType());
            }
        }
    }

    /**
     * 获取数据源表信息
     *
     * @param type 数据源类型
     * @return r
     */
    public DataSourceTable get(String type) {
        DataSourceType dataSourceType = DataSourceType.getByType(type);
        DataSourceTable dataSourceTable = this.map.get(dataSourceType);
        if (dataSourceTable != null) {
            return dataSourceTable;
        }
        throw new IllegalArgumentException("不支持的数据源类型: " + type);
    }

}
