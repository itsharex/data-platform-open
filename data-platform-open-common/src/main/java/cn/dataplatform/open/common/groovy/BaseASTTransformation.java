package cn.dataplatform.open.common.groovy;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/18
 * @since 1.0.0
 */
@GroovyASTTransformation
public class BaseASTTransformation implements ASTTransformation {

    /**
     * 执行AST转换的入口方法。
     *
     * @param nodes  AST节点数组，通常包含一个或多个AST节点。
     * @param source 源单元，包含了AST的上下文信息。
     */
    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST();
        if (moduleNode != null) {
            moduleNode.getClasses().forEach(
                    classNode -> this.transformClass(classNode, source)
            );
        }
    }

    /**
     * 转换类节点，遍历类中的所有方法并进行转换。
     *
     * @param classNode 类节点
     * @param source    源单元
     */
    private void transformClass(ClassNode classNode, SourceUnit source) {
        classNode.getMethods().forEach(
                methodNode -> this.transformMethod(methodNode, source)
        );
    }

    /**
     * 转换方法节点，检查方法中的表达式语句。
     *
     * @param methodNode 方法节点
     * @param source     源单元
     */
    private void transformMethod(MethodNode methodNode, SourceUnit source) {
        if (methodNode.getCode() != null) {
            methodNode.getCode().visit(new SecurityGroovyCodeVisitor(source));
        }
    }

}
