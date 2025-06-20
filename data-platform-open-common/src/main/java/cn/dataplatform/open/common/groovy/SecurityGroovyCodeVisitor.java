package cn.dataplatform.open.common.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;

import java.util.List;
import java.util.Map;

/**
 * 表达式语句转换器，检查方法调用、静态方法调用和构造器调用
 *
 * @author dingqianwen
 * @date 2025/6/18
 * @since 1.0.0
 */
public class SecurityGroovyCodeVisitor extends CodeVisitorSupport {

    /**
     * 不允许以下类型（使用全限定名），如果遇到则报错
     * 键为类的全限定名，值为禁止的方法列表（空列表表示完全禁止该类）
     */
    private static final Map<String, List<String>> NOT_ALLOWED = Map.ofEntries(
            Map.entry("java.lang.Runtime", List.of()),
            Map.entry("java.lang.System", List.of())
            // ..开源版本请自我添加禁止的类和方法
            // ..
    );

    private final SourceUnit sourceUnit;

    public SecurityGroovyCodeVisitor(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    /**
     * 访问表达式语句
     *
     * @param statement 表达式语句
     */
    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        this.checkForbiddenCalls(statement.getExpression());
        super.visitExpressionStatement(statement);
    }

    /**
     * 访问方法调用表达式
     *
     * @param call 方法调用表达式
     */
    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        this.checkMethodCall(call);
        super.visitMethodCallExpression(call);
    }

    /**
     * 访问静态方法调用表达式
     *
     * @param call 静态方法调用表达式
     */
    @Override
    public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
        this.checkStaticMethodCall(call);
        super.visitStaticMethodCallExpression(call);
    }

    /**
     * 访问构造器调用表达式
     *
     * @param call 构造器调用表达式
     */
    @Override
    public void visitConstructorCallExpression(ConstructorCallExpression call) {
        this.checkConstructorCall(call);
        super.visitConstructorCallExpression(call);
    }

    /**
     * 检查表达式中的方法调用、静态方法调用和构造器调用
     *
     * @param expr 表达式
     */
    private void checkForbiddenCalls(Expression expr) {
        if (expr instanceof MethodCallExpression) {
            this.checkMethodCall((MethodCallExpression) expr);
        } else if (expr instanceof StaticMethodCallExpression) {
            this.checkStaticMethodCall((StaticMethodCallExpression) expr);
        } else if (expr instanceof ConstructorCallExpression) {
            this.checkConstructorCall((ConstructorCallExpression) expr);
        }
    }

    /**
     * 检查方法调用
     *
     * @param call 方法调用表达式
     */
    private void checkMethodCall(MethodCallExpression call) {
        Expression objectExpr = call.getObjectExpression();
        // 例如 new Test().test()
        if (objectExpr instanceof ConstructorCallExpression ctorCall) {
            checkConstructorCall(ctorCall);
            ClassNode constructedType = ctorCall.getType();
            this.checkClassMethodCall(constructedType, call.getMethodAsString(), call);
        }
        // 例如 variable.test()
        else if (objectExpr instanceof VariableExpression varExpr) {
            this.checkClassMethodCall(varExpr.getOriginType(), call.getMethodAsString(), call);
        }
        // 例如 Test.test()
        else if (objectExpr instanceof ClassExpression classExpr) {
            this.checkClassMethodCall(classExpr.getType(), call.getMethodAsString(), call);
        }
    }

    /**
     * 检查静态方法调用
     *
     * @param call 静态方法调用表达式
     */
    private void checkStaticMethodCall(StaticMethodCallExpression call) {
        ClassNode declaringClass = call.getOwnerType();
        this.checkClassMethodCall(declaringClass, call.getMethod(), call);
    }

    /**
     * 检查构造器调用
     *
     * @param call 构造器调用表达式
     */
    private void checkConstructorCall(ConstructorCallExpression call) {
        ClassNode constructedType = call.getType();
        // 检查类及其父类是否在禁止列表中
        if (this.isClassForbidden(constructedType.getName())) {
            List<String> forbiddenMethods = NOT_ALLOWED.get(constructedType.getName());
            // 如果禁止方法列表为空（如Environment），表示完全禁止该类
            if (forbiddenMethods.isEmpty()) {
                String errorMsg = String.format("不允许实例化 %s 类或其子类",
                        constructedType.getNameWithoutPackage());
                this.sourceUnit.addError(new SyntaxException(errorMsg, call.getLineNumber(), call.getColumnNumber()));
            }
            // 否则允许实例化
        }
    }

    /**
     * 检查类方法调用
     *
     * @param classNode  类节点
     * @param methodName 方法名
     * @param callExpr   调用表达式
     */
    private void checkClassMethodCall(ClassNode classNode, String methodName, Expression callExpr) {
        // 检查类及其父类是否在禁止列表中
        String className = classNode.getName();
        if (this.isClassForbidden(className)) {
            List<String> forbiddenMethods = NOT_ALLOWED.get(this.findForbiddenClassName(className));
            // 情况1：完全禁止的类（如Environment）
            if (forbiddenMethods.isEmpty()) {
                String errorMsg = String.format("不允许调用 %s 类或其子类的任何方法",
                        classNode.getNameWithoutPackage());
                this.sourceUnit.addError(new SyntaxException(errorMsg, callExpr.getLineNumber(), callExpr.getColumnNumber()));
            }
            // 情况2：部分禁止的类（如Test）
            else if (forbiddenMethods.contains(methodName)) {
                String errorMsg = String.format("不允许调用 %s.%s() 方法或其子类的该方法",
                        classNode.getNameWithoutPackage(), methodName);
                this.sourceUnit.addError(new SyntaxException(errorMsg, callExpr.getLineNumber(), callExpr.getColumnNumber()));
            }
            // 其他情况：允许调用
        }
    }

    /**
     * 检查类或其父类是否在禁止列表中
     *
     * @param className 类名
     * @return 如果类或其父类在禁止列表中，返回true，否则返回false
     */
    private boolean isClassForbidden(String className) {
        // 先检查当前类是否直接禁止
        if (NOT_ALLOWED.containsKey(className)) {
            return true;
        }
        // 检查父类和接口
        try {
            Class<?> clazz = Class.forName(className);
            for (String forbiddenClass : NOT_ALLOWED.keySet()) {
                Class<?> forbiddenClazz = Class.forName(forbiddenClass);
                if (forbiddenClazz.isAssignableFrom(clazz)) {
                    return true;
                }
            }
        } catch (ClassNotFoundException e) {
            // 类加载失败，可能是Groovy动态类等，保守起见返回false
            return false;
        }
        return false;
    }

    /**
     * 查找类或其父类对应的禁止类名
     *
     * @param className 类名
     * @return 如果类或其父类在禁止列表中，返回禁止类名，否则返回原始类名
     */
    private String findForbiddenClassName(String className) {
        // 先检查当前类是否直接禁止
        if (NOT_ALLOWED.containsKey(className)) {
            return className;
        }
        // 检查父类和接口
        try {
            Class<?> clazz = Class.forName(className);
            for (String forbiddenClass : NOT_ALLOWED.keySet()) {
                Class<?> forbiddenClazz = Class.forName(forbiddenClass);
                if (forbiddenClazz.isAssignableFrom(clazz)) {
                    return forbiddenClass;
                }
            }
        } catch (ClassNotFoundException e) {
            // 类加载失败，返回原始类名
            return className;
        }
        return className;
    }

}
