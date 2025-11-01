package cn.dataplatform.open.web.service.datasource.test;

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
 * @date 2025/1/4
 * @since 1.0.0
 */

@Component
public class DataSourceTestManager implements ApplicationContextAware {

    private final Map<DataSourceType, DataSourceTest> map = new ConcurrentHashMap<>();

    /**
     * 设置应用上下文
     *
     * @param applicationContext 应用上下文
     * @throws BeansException 异常
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        Map<String, DataSourceTest> beansOfType = applicationContext.getBeansOfType(DataSourceTest.class);
        for (DataSourceTest dataSourceTest : beansOfType.values()) {
            DataSourceTest existing = this.map.put(dataSourceTest.getDataSourceType(), dataSourceTest);
            // 检查是否存在重复的处理器，防止写错
            if (existing != null) {
                throw new IllegalStateException("存在重复的数据源类型: " + dataSourceTest.getDataSourceType());
            }
        }
    }

    /**
     * 获取数据源
     *
     * @param type 数据源类型
     * @return r
     */
    public DataSourceTest get(String type) {
        DataSourceType dataSourceType = DataSourceType.getByType(type);
        DataSourceTest dataSourceTest = this.map.get(dataSourceType);
        if (dataSourceTest != null) {
            return dataSourceTest;
        }
        throw new IllegalArgumentException("不支持的数据源类型: " + type);
    }

}

