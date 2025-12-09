package cn.dataplatform.open.common.source;

import cn.dataplatform.open.common.annotation.Mask;
import cn.dataplatform.open.common.enums.DataSourceType;
import cn.dataplatform.open.common.enums.MaskType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.io.Closeable;

/**
 * 〈Source〉
 *
 * @author dqw
 * @since 1.0.0
 */
@Data
public abstract class Source implements Closeable {

    protected String code;
    protected String name;

    /**
     * 连接信息
     */
    @Getter
    @NotBlank
    protected String url;
    /**
     * 可为空
     */
    @Nullable
    protected String username;
    @Mask(type = MaskType.PASSWORD)
    @Nullable
    protected String password;

    protected Boolean isEnableHealth;

    /**
     * 编码
     *
     * @return 编码
     */
    public String code() {
        return this.code;
    }

    /**
     * 名称
     *
     * @return 名称
     */
    public String name() {
        return this.name;
    }

    /**
     * 数据源类型
     *
     * @return 类型
     */
    public abstract DataSourceType type();

    /**
     * 是否启用健康检查
     *
     * @return true启用
     */
    public Boolean isEnableHealth() {
        return this.isEnableHealth;
    }

    /**
     * 健康检查
     *
     * @return true健康
     */
    public abstract Boolean health() throws Exception;

}