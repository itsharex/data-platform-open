package cn.dataplatform.open.flow.core.component.event.convert;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import lombok.SneakyThrows;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/7
 * @since 1.0.0
 */
public class MongoDataConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();


    /**
     * 处理debezium的数据，将JSON字符串转换为Map对象
     * <p>
     * {"_id": {"$oid": "68441d6c9aed3b154a05497c"},"name": {"f1": "value1","f2": "value2"},"age": 30,"email": "example@example.com1","createdAt": {"$date": 1749294444747}}
     *
     * @param json     JSON字符串
     * @param timezone 默认GMT+08
     * @return 转换后的Map对象
     */
    @SneakyThrows
    public static Map<String, Object> convert(String json, String timezone) {
        JsonNode rootNode = MAPPER.readTree(json);
        return MongoDataConverter.convertNode(rootNode, timezone);
    }

    /**
     * 转换节点值
     *
     * @param node n
     * @return r
     */
    private static Map<String, Object> convertNode(JsonNode node, String timezone) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                result.put(key, MongoDataConverter.processValue(value, timezone));
            }
        }
        return result;
    }

    /**
     * 处理转换值
     *
     * @param value v
     * @return r
     */
    private static Object processValue(JsonNode value, String timezone) {
        if (value.isObject()) {
            // 处理MongoDB特殊类型
            ObjectNode objectNode = (ObjectNode) value;
            // _id.$oid
            if (objectNode.size() == 1) {
                if (objectNode.has("$oid")) {
                    return objectNode.get("$oid").asText();
                } else if (objectNode.has("$date")) {
                    long date = objectNode.get("$date").asLong();
                    return LocalDateTimeUtil.of(date, TimeZone.getTimeZone(timezone));
                } else if (objectNode.has("$numberLong")) {
                    return objectNode.get("$numberLong").asText();
                } else if (objectNode.has("$numberDecimal")) {
                    return objectNode.get("$numberDecimal").asText();
                } else if (objectNode.has("$binary")) {
                    return objectNode.get("$binary").asText();
                }
            }
            // 常规对象 name.f1 name.f2
            return MongoDataConverter.convertNode(value, timezone);
        } else if (value.isArray()) {
            // 处理数组
            ArrayNode arrayNode = (ArrayNode) value;
            Object[] array = new Object[arrayNode.size()];
            for (int i = 0; i < arrayNode.size(); i++) {
                array[i] = MongoDataConverter.processValue(arrayNode.get(i), timezone);
            }
            return array;
        } else if (value.isValueNode()) {
            // 处理基本值
            ValueNode valueNode = (ValueNode) value;
            if (valueNode.isNumber()) {
                if (valueNode.isInt()) {
                    return valueNode.asInt();
                } else if (valueNode.isLong()) {
                    return valueNode.asLong();
                } else {
                    return valueNode.asDouble();
                }
            } else if (valueNode.isBoolean()) {
                return valueNode.asBoolean();
            } else {
                return valueNode.asText();
            }
        }
        return null;
    }

}
