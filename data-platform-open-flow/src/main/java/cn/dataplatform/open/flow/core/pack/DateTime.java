package cn.dataplatform.open.flow.core.pack;

import java.io.Serial;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/13
 * @since 1.0.0
 */
public class DateTime extends cn.hutool.core.date.DateTime {

    @Serial
    private static final long serialVersionUID = -5395712593979185936L;

    public DateTime() {
        super();
    }

    public DateTime(Date value) {
        super(value);
    }

    public DateTime(LocalDateTime value) {
        super(value);
    }

    public DateTime(TemporalAccessor value) {
        super(value);
    }

    /**
     * 格式化日期
     *
     * @param format 格式
     * @return 格式化后的日期
     */
    public String format(String format) {
        return this.toString(format);
    }

}
