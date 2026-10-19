package cn.edu.bookstore.common.security;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 登录失败锁定逻辑测试。
 *
 * <p>测试手法：让 {@link StringRedisTemplate} 在第一次调用时抛异常，组件会自动切到
 * 本地内存降级分支 —— 这样无需真实 Redis 就能覆盖完整的计数 / 锁定 / 解锁语义。
 * 降级分支本身也是线上会走到的路径（Redis 故障），因此顺带把它测掉。
 *
 * @author 毕设项目组
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("登录失败锁定")
class LoginAttemptGuardTest {

    private static final String SCOPE_USER = "user";
    private static final String SCOPE_ADMIN = "admin";
    private static final String PHONE = "13800138000";

    @Mock
    private StringRedisTemplate redisTemplate;

    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        // 模拟 Redis 故障：组件会捕获并降级为本地内存计数（同时也是被测路径之一）
        when(redisTemplate.getExpire(anyString(), any())).thenThrow(new RuntimeException("redis down"));
    }

    /** 构造一个使用给定策略的守卫；Redis 已在 setUp 中被置为不可用 */
    private LoginAttemptGuard guardWith(int maxFailures, boolean enabled) {
        AppProperties.LoginGuard cfg = new AppProperties.LoginGuard();
        cfg.setEnabled(enabled);
        cfg.setMaxFailures(maxFailures);
        cfg.setWindowMinutes(30);
        cfg.setLockMinutes(15);
        appProperties.setLoginGuard(cfg);
        return new LoginAttemptGuard(redisTemplate, appProperties);
    }

    @Test
    @DisplayName("失败次数未达阈值时不锁定")
    void belowThresholdDoesNotLock() {
        LoginAttemptGuard guard = guardWith(5, true);

        for (int i = 0; i < 4; i++) {
            boolean locked = guard.recordFailure(SCOPE_USER, PHONE);
            assertFalse(locked, "第 " + (i + 1) + " 次失败不应触发锁定");
        }

        assertDoesNotThrow(() -> guard.checkNotLocked(SCOPE_USER, PHONE),
                "只失败 4 次时正常用户仍应能登录");
    }

    @Test
    @DisplayName("达到阈值的那一次失败即触发锁定")
    void reachingThresholdLocks() {
        LoginAttemptGuard guard = guardWith(5, true);

        for (int i = 1; i <= 4; i++) {
            guard.recordFailure(SCOPE_USER, PHONE);
        }
        assertTrue(guard.recordFailure(SCOPE_USER, PHONE), "第 5 次失败应返回「已锁定」");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.checkNotLocked(SCOPE_USER, PHONE));
        assertEquals(ErrorCode.BIZ_ERROR, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("锁定"), "提示语应说明账号已被锁定，实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("分钟"), "提示语应告知多久后可重试，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("锁定期间即使密码正确也会被拒（拒绝发生在口令校验前）")
    void lockedAccountRejectsEvenCorrectPassword() {
        LoginAttemptGuard guard = guardWith(3, true);
        for (int i = 0; i < 3; i++) {
            guard.recordFailure(SCOPE_ADMIN, "admin");
        }

        // 守卫只负责「是否放行」，它并不知道口令对错 —— 这正是防护有效的原因
        assertThrows(BusinessException.class, () -> guard.checkNotLocked(SCOPE_ADMIN, "admin"));
    }

    @Test
    @DisplayName("reset 同时清掉失败计数与锁定标记，账号立即恢复可用")
    void resetUnlocksAccount() {
        LoginAttemptGuard guard = guardWith(3, true);
        for (int i = 0; i < 3; i++) {
            guard.recordFailure(SCOPE_USER, PHONE);
        }
        assertThrows(BusinessException.class, () -> guard.checkNotLocked(SCOPE_USER, PHONE));

        guard.reset(SCOPE_USER, PHONE);

        assertDoesNotThrow(() -> guard.checkNotLocked(SCOPE_USER, PHONE));
        // 解锁后计数从 0 重新开始：此时再失败 2 次不应立刻重新锁定
        guard.recordFailure(SCOPE_USER, PHONE);
        guard.recordFailure(SCOPE_USER, PHONE);
        assertDoesNotThrow(() -> guard.checkNotLocked(SCOPE_USER, PHONE),
                "解锁后应重新计数，而不是「刚解锁就被下一次失败重新锁上」");
    }

    @Test
    @DisplayName("开关关闭时永不锁定（保留给管理员应急用）")
    void disabledGuardNeverLocks() {
        LoginAttemptGuard guard = guardWith(3, false);

        for (int i = 0; i < 20; i++) {
            assertFalse(guard.recordFailure(SCOPE_USER, PHONE), "开关关闭时不应产生锁定");
        }
        assertDoesNotThrow(() -> guard.checkNotLocked(SCOPE_USER, PHONE));
    }

    @Test
    @DisplayName("不同账号域互不影响：锁住 APP 用户不会波及同名管理员")
    void scopesAreIsolated() {
        LoginAttemptGuard guard = guardWith(2, true);

        guard.recordFailure(SCOPE_USER, "shared");
        guard.recordFailure(SCOPE_USER, "shared");

        assertThrows(BusinessException.class, () -> guard.checkNotLocked(SCOPE_USER, "shared"));
        assertDoesNotThrow(() -> guard.checkNotLocked(SCOPE_ADMIN, "shared"),
                "user 域的锁定不应影响 admin 域");
    }

    @Test
    @DisplayName("不同账号之间的计数互相独立")
    void identitiesAreIsolated() {
        LoginAttemptGuard guard = guardWith(2, true);

        guard.recordFailure(SCOPE_USER, "13800138001");
        guard.recordFailure(SCOPE_USER, "13800138001");

        assertThrows(BusinessException.class, () -> guard.checkNotLocked(SCOPE_USER, "13800138001"));
        assertDoesNotThrow(() -> guard.checkNotLocked(SCOPE_USER, "13800138002"),
                "锁定只针对被攻击的那个账号，不能连坐其他用户");
    }

    @Test
    @DisplayName("Redis 异常时降级为本地计数而不是放行，且不向调用方抛错")
    void redisFailureDegradesInsteadOfOpeningUp() {
        LoginAttemptGuard guard = guardWith(3, true);

        // 第一次调用就会触发 Redis 异常，若未做降级这里会直接抛出 RedisConnectionFailureException
        assertDoesNotThrow(() -> {
            guard.recordFailure(SCOPE_USER, PHONE);
            guard.recordFailure(SCOPE_USER, PHONE);
            guard.recordFailure(SCOPE_USER, PHONE);
        });

        assertThrows(BusinessException.class, () -> guard.checkNotLocked(SCOPE_USER, PHONE),
                "Redis 挂掉不等于防护失效，本地内存必须顶上");
    }

    @Test
    @DisplayName("账号标识为空时不做任何计数（避免脏数据污染全局键）")
    void blankIdentityIsIgnored() {
        LoginAttemptGuard guard = guardWith(1, true);

        assertDoesNotThrow(() -> {
            guard.recordFailure(SCOPE_USER, null);
            guard.recordFailure(SCOPE_USER, "  ");
            guard.checkNotLocked(SCOPE_USER, null);
        });
    }
}
