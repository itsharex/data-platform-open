package cn.dataplatform.open.query.store.entity;

import cn.dataplatform.open.common.enums.Status;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/2
 * @since 1.0.0
 */
@TableName("query_template")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class QueryTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    /**
     * 模板配置
     * <p>
     * 例:select * from table where id = ${id}
     */
    private String template;

    private String workspaceCode;

    /**
     * 启用,禁用
     *
     * @see Status
     */
    private String status;

    private String description;

    private String dataSourceCode;

    /**
     * 超时时间
     */
    private Integer timeout;

    private String secret;

    private String currentVersion;

    private String publishVersion;

    private Long createUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

}
