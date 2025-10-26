package cn.dataplatform.open.query.store.entity;

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
 * @date 2025/1/3
 * @since 1.0.0
 */
@TableName("data_source")
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class DataSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    /**
     * MySQL Doris StarRocks Oracle等
     */
    private String type;

    private String url;

    private String username;

    private String password;

    private String driver;

    private Integer maxPoolSize;

    /**
     * 启用,禁用
     */
    private String status;

    private Long createUserId;

    private String workspaceCode;

    private String feNodes;
    private String beNodes;

    private String partitioningAlgorithm;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;

}
