package cn.dataplatform.open.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/30
 * @since 1.0.0
 */
@AllArgsConstructor
@Getter
public enum Micrometer {

    /**
     * 数据流数据处理数量
     */
    FLOW_RUN_PROCESS_NUMBER("dp_flow_run_process_number", "数据流数据处理数量"),
    /**
     * 数据流数据处理耗时
     */
    FLOW_RUN_TIME("dp_flow_run_time", "数据流数据处理耗时"),

    /**
     * 数据流数据处理异常次数
     */
    FLOW_RUN_ERROR("dp_flow_run_error", "数据流数据处理异常次数"),

    /**
     * 查询模板查询耗时
     */
    QUERY_TEMPLATE_QUERY_TIME("dp_query_template_query_time", "查询模板查询耗时"),
    /**
     * 查询模板查询次数
     */
    QUERY_TEMPLATE_QUERY_NUMBER("dp_query_template_query_number", "查询模板查询次数"),
    /**
     * 查询模板缓存命中数
     */
    QUERY_TEMPLATE_QUERY_CACHE_HIT_NUMBER("dp_query_template_query_cache_hit_number", "查询模板缓存命中数"),
    /**
     * 查询模板查询异常次数
     */
    QUERY_TEMPLATE_QUERY_ERROR_NUMBER("dp_query_template_query_error_number", "查询模板查询异常次数"),
    ;

    private final String name;
    private final String description;


    /**
     * flowCode
     */
    public static final String FLOW_CODE = "flowCode";
    /**
     * 父组件编码
     */
    public static final String PARENT_FLOW_COMPONENT_CODE = "parentFlowComponentCode";
    /**
     * flowComponentCode
     */
    public static final String FLOW_COMPONENT_CODE = "flowComponentCode";
    /**
     * queryMethod
     */
    public static final String QUERY_METHOD = "queryMethod";
    /**
     * queryTemplateCode
     */
    public static final String QUERY_TEMPLATE_CODE = "queryTemplateCode";

    /**
     * workspaceCode
     */
    public static final String WORKSPACE_CODE = "workspaceCode";

}
