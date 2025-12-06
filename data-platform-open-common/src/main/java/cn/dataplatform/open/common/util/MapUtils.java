package cn.dataplatform.open.common.util;

import cn.hutool.core.map.CaseInsensitiveMap;
import org.springframework.util.LinkedCaseInsensitiveMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/11/29
 * @since 1.0.0
 */
public class MapUtils {


    /**
     * 转为忽略大小写的Map
     *
     * @param map 原始Map
     * @return 忽略大小写的Map
     */
    public static Map<String, Object> toCaseInsensitiveMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        if (map.isEmpty()) {
            return map;
        }
        // 已经忽略大小写
        if (map instanceof CaseInsensitiveMap) {
            return map;
        }
        if (map instanceof LinkedCaseInsensitiveMap) {
            return map;
        }
        // 按照原字段大小存储，但查询时忽略大小写
        LinkedCaseInsensitiveMap<Object> caseInsensitiveMap = new LinkedCaseInsensitiveMap<>(map.size());
        caseInsensitiveMap.putAll(map);
        return caseInsensitiveMap;
    }

    /**
     * 转为忽略大小写的Map列表
     *
     * @param maps 原始Map列表
     * @return 忽略大小写的Map列表
     */
    public static List<Map<String, Object>> toCaseInsensitiveMap(List<Map<String, Object>> maps) {
        if (maps == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>(maps.size());
        for (Map<String, Object> map : maps) {
            Map<String, Object> caseInsensitiveMap = MapUtils.toCaseInsensitiveMap(map);
            result.add(caseInsensitiveMap);
        }
        return result;
    }

}
