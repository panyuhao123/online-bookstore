package cn.edu.bookstore.common.observability;

import cn.edu.bookstore.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向 {@code /actuator/info} 输出「本实例当前是什么形态」。
 *
 * <p>解决的问题：排查线上问题时，第一个要确认的往往是「这个实例到底加载了哪份配置」——
 * 是 local 还是 prod profile？支付走的是模拟还是支付宝？文档入口关没关？
 * 反爆破和令牌吊销开着吗？以前只能去翻配置文件或登录机器，现在一次 GET 就能看清，
 * 而且看到的是<b>运行时真实生效的值</b>（而不是配置文件里写的值）。
 *
 * <p>安全边界：这里只暴露「开关与枚举」类信息（true/false、mock/alipay、local/minio），
 * <b>绝不</b>输出密钥、数据库口令、对象存储凭证 —— 因此本端点才允许匿名访问。
 * 新增字段时请守住这条线。
 *
 * <p>与 {@code management.info.env.enabled} 的区别：那个开关输出的是 Spring Environment
 * 里的原始属性（会把大量无关配置一并吐出，且可能包含敏感项），本类只挑选需要展示的几项，
 * 既精简又可控。
 *
 * @author 毕设项目组
 */
@Component
@RequiredArgsConstructor
public class BookstoreInfoContributor implements InfoContributor {

    private final AppProperties appProperties;
    private final Environment environment;

    @Override
    public void contribute(Info.Builder builder) {
        // LinkedHashMap 保证输出顺序稳定，便于人读与脚本断言
        Map<String, Object> detail = new LinkedHashMap<>();
        List<String> profiles = List.of(environment.getActiveProfiles());
        detail.put("profiles", profiles.isEmpty() ? List.of("default") : profiles);
        detail.put("payType", appProperties.getPay().getType());
        detail.put("storageType", appProperties.getStorage().getType());
        detail.put("jwtExpireHours", appProperties.getJwt().getExpireHours());
        detail.put("tokenRevocationEnabled", appProperties.getJwt().getRevocation().isEnabled());
        detail.put("loginGuardEnabled", appProperties.getLoginGuard().isEnabled());
        detail.put("loginGuardMaxFailures", appProperties.getLoginGuard().getMaxFailures());
        detail.put("cacheEnabled", appProperties.getCache().isEnabled());
        detail.put("cacheEvictAfterCommit", appProperties.getCache().isEvictAfterCommit());
        detail.put("cacheTtlMinutes", appProperties.getCache().getTtlMinutes());
        detail.put("docsEnabled", appProperties.getDocs().isEnabled());
        detail.put("metricsEnabled", appProperties.getObservability().isMetricsEnabled());

        builder.withDetail("bookstore", detail);
    }
}
