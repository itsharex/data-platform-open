package cn.dataplatform.open.support.util;

import cn.hutool.cache.CacheUtil;
import cn.hutool.cache.impl.LRUCache;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.SneakyThrows;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/4/28
 * @since 1.0.0
 */
public class FreeMarkerUtils {

    public static final String DEBUG = "debug";

    private static final Configuration CONFIGURATION;
    /**
     * 模板缓存 (1000个模板，10分钟过期)
     */
    private static final LRUCache<String, Template> TEMPLATE_CACHE = CacheUtil.newLRUCache(
            1000, 1000 * 60 * 10);
    private static final ReentrantLock LOCK = new ReentrantLock();

    static {
        // 初始化FreeMarker配置
        CONFIGURATION = new Configuration(Configuration.VERSION_2_3_34);
        CONFIGURATION.setDefaultEncoding("UTF-8");
        // 输出异常堆栈并继续执行
        CONFIGURATION.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        // 避免日志污染
        CONFIGURATION.setLogTemplateExceptions(false);
        // 统一异常处理
        CONFIGURATION.setWrapUncheckedExceptions(true);
        CONFIGURATION.setFallbackOnNullLoopVariable(false);
        CONFIGURATION.setClassicCompatible(true);
    }

    /**
     * 处理模板并返回结果
     *
     * @param content   模板内容字符串
     * @param parameter 数据模型Map
     * @return 处理后的结果字符串
     */
    @SneakyThrows
    public static String processTemplate(String name, String content, Map<String, Object> parameter) {
        Objects.requireNonNull(parameter);
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (StrUtil.isBlank(content)) {
            throw new IllegalArgumentException("模板内容不能为空");
        }
        // 调试用,可以打印出所有的参数
        parameter.put(DEBUG, JSON.toJSONString(parameter));
        // 创建临时模板
        Template template = FreeMarkerUtils.getTemplateFromCache(name, content);
        // 处理模板
        StringWriter writer = new StringWriter();
        template.process(parameter, writer);
        return writer.toString();
    }

    /**
     * 从缓存获取模板，如果不存在则创建并缓存
     *
     * @param name    模板名称
     * @param content 模板内容
     * @return 模板对象
     */
    private static Template getTemplateFromCache(String name, String content) throws Exception {
        // 内容可能会发生变化，所以使用hashCode作为缓存key的一部分
        String cacheKey = name + content.hashCode();
        Template template = TEMPLATE_CACHE.get(cacheKey);
        if (template != null) {
            return template;
        }
        LOCK.lock();
        try {
            // 双重检查
            template = TEMPLATE_CACHE.get(cacheKey);
            if (template == null) {
                // 使用StringReader作为模板源
                StringReader reader = new StringReader(content);
                // 创建临时模板
                template = new Template(name, reader, CONFIGURATION);
                TEMPLATE_CACHE.put(cacheKey, template);
            }
            return template;
        } finally {
            LOCK.unlock();
        }
    }

}
