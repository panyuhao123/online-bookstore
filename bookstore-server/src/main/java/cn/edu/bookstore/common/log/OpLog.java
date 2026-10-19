package cn.edu.bookstore.common.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在管理端写操作的方法上，由 {@link OpLogAspect} 自动记录。
 *
 * <p>为什么用注解而不是在每个方法里手写一行「saveLog(...)」：
 * <ul>
 *     <li><b>不污染业务代码</b>：业务方法只关心业务，日志是横切关注点；</li>
 *     <li><b>不会漏</b>：新增管理端接口时，写上注解即被记录，
 *         而手写调用很容易在复制粘贴新接口时忘掉；</li>
 *     <li><b>信息集中</b>：模块与动作在注解里声明，改文案不用翻业务逻辑。</li>
 * </ul>
 *
 * <p>注意命名：注解叫 {@code OpLog}，实体叫 {@code OperationLog}。
 * 两者分开命名是为了避免在切面里同时 import 时产生类名冲突。
 *
 * @author 毕设项目组
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 业务模块，如「图书管理」 */
    String module();

    /** 操作动作，如「新增图书」 */
    String action();
}
