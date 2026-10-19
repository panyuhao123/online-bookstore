package cn.edu.bookstore.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度开关。
 *
 * <p>单独抽一个配置类，是为了让「关闭定时任务」这件事有明确的落点：
 * 把 {@code bookstore.order.auto-cancel-enabled} 设为 false，本类不加载，
 * {@code @EnableScheduling} 不生效，连调度线程池都不会创建。
 *
 * <p>与缓存开关（CacheConfig）用的是同一套思路 —— 所有「附加能力」都能一键关掉，
 * 排障时可以把系统退化成最小可运行形态。
 *
 * @author 毕设项目组
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bookstore.order.auto-cancel-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    public SchedulingConfig() {
        log.info("定时任务调度已启用（超时订单自动取消）");
    }
}
