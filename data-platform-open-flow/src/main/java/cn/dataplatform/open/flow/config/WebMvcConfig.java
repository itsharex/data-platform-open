package cn.dataplatform.open.flow.config;

import cn.dataplatform.open.common.introspect.MaskJacksonAnnotationIntrospector;
import cn.dataplatform.open.flow.interceptor.TraceInterceptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;


/**
 * 〈一句话功能简述〉<br>
 * 〈mvc Interceptor〉
 *
 * @author 丁乾文
 * @date 2021/6/17
 * @since 1.0.0
 */
@Component
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private TraceInterceptor traceInterceptor;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 静态资源不拦截
     */
    private static final List<String> STATIC_RESOURCE = Arrays.asList(
            "/favicon.ico/**",
            "/error/**");

    /**
     * @param registry 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.traceInterceptor).addPathPatterns("/**")
                .excludePathPatterns(STATIC_RESOURCE).order(1);
    }


    @Override
    public void configureMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        // 解决enum不匹配问题
        this.objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
        this.objectMapper.setAnnotationIntrospector(new MaskJacksonAnnotationIntrospector());
    }


}
