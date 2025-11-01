package cn.dataplatform.open.flow.core.component.event.convert;

import io.debezium.spi.converter.CustomConverter;
import io.debezium.spi.converter.RelationalColumn;
import org.apache.kafka.connect.data.SchemaBuilder;

import java.nio.ByteBuffer;
import java.util.Properties;


/**
 * BinaryConverter
 *
 * @author dqw
 * @date 2025/1/8 11:56
 * @since 1.0.0
 */
public class BinaryConverter implements CustomConverter<SchemaBuilder, RelationalColumn> {

    @Override
    public void configure(Properties props) {
    }

    /**
     * blob处理
     *
     * @param column       c
     * @param registration r
     */
    @Override
    public void converterFor(RelationalColumn column,
                             ConverterRegistration<SchemaBuilder> registration) {
        // 只处理二进制类型的列
        if ("BYTES".equals(column.typeName())
                || "BLOB".equals(column.typeName())
                || "TINYBLOB".equals(column.typeName())
                || "MEDIUMBLOB".equals(column.typeName())
                || "LONGBLOB".equals(column.typeName())
                || "VARBINARY".equals(column.typeName())
                || "BINARY".equals(column.typeName())
                // pgsql bytea
                || "bytea".equalsIgnoreCase(column.typeName())
        ) {
            registration.register(SchemaBuilder.bytes(), value -> {
                if (value == null) {
                    return null;
                }
                // 修复同步blob字段问题
                // Caused by: java.sql.SQLException: Cannot convert class java.nio.HeapByteBuffer to SQL type requested due to
                if (value instanceof ByteBuffer buffer) {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    return bytes;
                }
                // 如果不是ByteBuffer，按原样返回
                return value;
            });
        }
    }

}