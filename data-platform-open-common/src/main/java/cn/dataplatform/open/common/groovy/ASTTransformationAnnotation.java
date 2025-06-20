package cn.dataplatform.open.common.groovy;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/18
 * @since 1.0.0
 */
@GroovyASTTransformationClass(classes = {BaseASTTransformation.class})
public @interface ASTTransformationAnnotation {
}
