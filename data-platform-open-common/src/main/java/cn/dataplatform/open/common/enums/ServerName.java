package cn.dataplatform.open.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/26
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum ServerName {

    /**
     * 负责数据流运行，建议至少2个节点，增加可用性
     */
    FLOW_SERVER("data-platform-flow", "数据流服务"),
    /**
     * 配置服务，页面配置处理
     */
    WEB_SERVER("data-platform-web", "配置服务"),
    /**
     * 查询模板，对外提供查询
     */
    QUERY_SERVER("data-platform-query", "查询服务"),
    /**
     * 告警分发，数据对齐、数据源健康检查等处理
     */
    SUPPORT_SERVER("data-platform-support", "支持服务"),
    ;

    private final String value;
    private final String name;


    /**
     * 根据值获取枚举名称
     *
     * @param value 枚举值
     * @return 枚举名称
     */
    public static ServerName getByValue(String value) {
        for (ServerName serverName : ServerName.values()) {
            if (serverName.getValue().equals(value)) {
                return serverName;
            }
        }
        throw new IllegalArgumentException("No enum constant for value: " + value);
    }

}
