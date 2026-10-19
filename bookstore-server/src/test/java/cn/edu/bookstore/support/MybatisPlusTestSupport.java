package cn.edu.bookstore.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 单测环境下的 MyBatis-Plus 元数据初始化。
 *
 * <p>背景：业务代码大量使用 {@code LambdaUpdateWrapper.set(Book::getSales, ...)} 这类
 * lambda 条件构造器。lambda 里的方法引用不是「列名字符串」，MyBatis-Plus 需要先把
 * 实体类解析成 {@code TableInfo}（列名、主键、逻辑删除等元数据）才能把它翻译成 SQL 片段。
 * 这一步平时由 MyBatis 扫描 Mapper 时自动完成。
 *
 * <p>纯 Mockito 单测不起 Spring 容器，也就没有人去做这件事 —— 结果是
 * {@code MybatisPlusException: can not find lambda cache for this entity}，
 * 报错信息像是在说「实体找不到」，实际是「元数据没注册」，很容易被误判成代码写错。
 *
 * <p>因此这里手工触发一次注册：只需要一个 {@link MapperBuilderAssistant}，
 * 把实体类逐个交给 {@link TableInfoHelper} 即可。注册结果按类缓存，
 * 重复调用不会重复解析。
 */
public final class MybatisPlusTestSupport {

    private MybatisPlusTestSupport() {
    }

    /** 为给定实体注册 TableInfo，使其 lambda 条件构造器可以在无容器环境下解析列名 */
    public static void initTableInfo(Class<?>... entities) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
