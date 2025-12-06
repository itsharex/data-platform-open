package cn.dataplatform.open.flow.service.core.component.event.convert;

import cn.hutool.core.util.StrUtil;
import io.debezium.spi.converter.CustomConverter;
import io.debezium.spi.converter.RelationalColumn;
import org.apache.kafka.connect.data.SchemaBuilder;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * DateTimeConverter
 *
 * @author dqw
 * @date 2025/1/8 11:56
 * @since 1.0.0
 */
public class DateTimeConverter implements CustomConverter<SchemaBuilder, RelationalColumn> {

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_DATE;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_TIME;

    private final DateTimeFormatter datetimeFormatter = DateTimeFormatter.ISO_DATE_TIME;

    private final DateTimeFormatter timestampFormatter = DateTimeFormatter.ISO_DATE_TIME;

    private ZoneId timestampZoneId;

    /**
     * 配置
     *
     * @param props p
     */
    @Override
    public void configure(Properties props) {
        String property = props.getProperty("timezone");
        if (StrUtil.isBlank(property)) {
            property = ZoneId.systemDefault().getId();
        }
        this.timestampZoneId = ZoneId.of(property);
    }

    /**
     * 时间处理
     *
     * @param column       c
     * @param registration r
     */
    @Override
    public void converterFor(RelationalColumn column, ConverterRegistration<SchemaBuilder> registration) {
        String sqlType = column.typeName().toUpperCase();
        SchemaBuilder schemaBuilder = null;
        Converter converter = null;
        if ("DATE".equals(sqlType)) {
            schemaBuilder = SchemaBuilder.string().optional().name("com.darcytech.debezium.date.string");
            converter = this::convertDate;
        }
        if ("TIME".equals(sqlType)) {
            schemaBuilder = SchemaBuilder.string().optional().name("com.darcytech.debezium.time.string");
            converter = this::convertTime;
        }
        if ("DATETIME".equals(sqlType)) {
            schemaBuilder = SchemaBuilder.string().optional().name("com.darcytech.debezium.datetime.string");
            converter = this::convertDateTime;
        }
        if ("TIMESTAMP".equals(sqlType)) {
            schemaBuilder = SchemaBuilder.string().optional().name("com.darcytech.debezium.timestamp.string");
            converter = this::convertTimestamp;
        }
        if (schemaBuilder != null) {
            registration.register(schemaBuilder, converter);
        }
    }

    /**
     * 处理LocalDate类型
     *
     * @param input i
     * @return r
     */
    private String convertDate(Object input) {
        if (input == null) return null;
        if (input instanceof LocalDate) {
            return this.dateFormatter.format((LocalDate) input);
        }
        if (input instanceof Integer) {
            LocalDate date = LocalDate.ofEpochDay((Integer) input);
            return this.dateFormatter.format(date);
        }
        return String.valueOf(input);

    }

    /**
     * 处理Duration类型
     *
     * @param input i
     * @return r
     */
    private String convertTime(Object input) {
        if (input == null) return null;
        if (input instanceof Duration duration) {
            long seconds = duration.getSeconds();
            int nano = duration.getNano();
            LocalTime time = LocalTime.ofSecondOfDay(seconds).withNano(nano);
            return this.timeFormatter.format(time);
        }
        return String.valueOf(input);
    }

    /**
     * 处理LocalDateTime类型
     *
     * @param input i
     * @return r
     */
    private String convertDateTime(Object input) {
        if (input == null) return null;
        if (input instanceof LocalDateTime) {
            return this.datetimeFormatter.format((LocalDateTime) input).replaceAll("T", " ");
        }
        return String.valueOf(input);
    }

    /**
     * 处理时间戳类型
     *
     * @param input i
     * @return r
     */
    private String convertTimestamp(Object input) {
        if (input == null) return null;
        if (input instanceof ZonedDateTime zonedDateTime) {
            // mysql的timestamp会转成UTC存储,这里的zonedDatetime都是UTC时间
            LocalDateTime localDateTime = zonedDateTime.withZoneSameInstant(this.timestampZoneId).toLocalDateTime();
            return this.timestampFormatter.format(localDateTime).replaceAll("T", " ");
        } else if (input instanceof Instant) {
            // 转为LocalDateTime
            LocalDateTime localDateTime = LocalDateTime.ofInstant((Instant) input, this.timestampZoneId);
            return this.timestampFormatter.format(localDateTime).replaceAll("T", " ");
        }
        return String.valueOf(input);
    }

}