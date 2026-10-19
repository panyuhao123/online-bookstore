package cn.edu.bookstore;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 线上书店 APP 后端服务启动类。
 *
 * <p>技术栈：SpringBoot 3 + MyBatis-Plus + MySQL 8。
 * 对外提供 RESTful 接口，供 uni-app 移动端 APP 与 Vue 管理后台调用。
 *
 * @author 毕设项目组
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("cn.edu.bookstore.mapper")
public class BookstoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookstoreApplication.class, args);
    }
}
