package cn.dataplatform.open.common.groovy;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.jayway.jsonpath.JsonPath;
import groovy.lang.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.reflect.Method;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/3/7
 * @since 1.0.0
 */
@Slf4j
public class GroovySupport {

    public static final String GENERATE_SCRIPT_NAME = "generateScriptName";
    public static final Method GENERATE_SCRIPT_NAME_METHOD;

    public static final GroovyShell GROOVY_SHELL;


    static {
        try {
            GENERATE_SCRIPT_NAME_METHOD = GroovyShell.class.getDeclaredMethod(GENERATE_SCRIPT_NAME);
            GENERATE_SCRIPT_NAME_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        ClassLoader classLoader = GroovyShell.class.getClassLoader();
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(new ASTTransformationCustomizer(ASTTransformationAnnotation.class, classLoader));
        // 添加默认导入
        config.addCompilationCustomizers(new ImportCustomizer().addImports(
                        // hutool
                        DateTime.class.getName(), DateUtil.class.getName(), StrUtil.class.getName(),
                        cn.hutool.core.lang.UUID.class.getName(),
                        // 操作json
                        JsonPath.class.getName(), JSON.class.getName()
                )
        );
        GROOVY_SHELL = new GroovyShell(classLoader, config);
    }

    /**
     * 预编译脚本
     *
     * @param scriptText 脚本内容
     * @return r 编译后的类
     */
    @SneakyThrows
    public static Class<?> compile(String scriptText) {
        GroovyClassLoader classLoader = GROOVY_SHELL.getClassLoader();
        // 预编译脚本
        String fileName = (String) GENERATE_SCRIPT_NAME_METHOD.invoke(GROOVY_SHELL);
        GroovyCodeSource gcs = new GroovyCodeSource(scriptText, fileName, GroovyShell.DEFAULT_CODE_BASE);
        return classLoader.parseClass(gcs, false);
    }

    /**
     * 脚本运行
     *
     * @param scriptClass 脚本类
     * @param binding     绑定参数
     */
    public static Object run(Class<?> scriptClass, Binding binding) {
        Script script = InvokerHelper.createScript(scriptClass, binding);
        return script.run();
    }

}
