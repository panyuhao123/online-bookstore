package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.observability.BusinessMetrics;
import cn.edu.bookstore.common.security.LoginAttemptGuard;
import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.common.util.PasswordUtil;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.dto.LoginRequest;
import cn.edu.bookstore.model.vo.LoginVO;
import cn.edu.bookstore.service.CaptchaService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户登录的哈希透明升级行为测试。
 *
 * <p>关注三件事：老数据登录后被改写成 BCrypt、已是 BCrypt 的不做多余写入、
 * 升级写入失败不能影响用户正常登录。
 *
 * @author 毕设项目组
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("用户登录：历史密码哈希透明升级")
class UserServiceImplPasswordUpgradeTest {

    private static final String PHONE = "13800138000";
    private static final String RAW_PASSWORD = "123456";

    /** seed.sql 中该账号的历史 MD5 密文与盐 */
    private static final String LEGACY_HASH = "58ab4bd28431ad3abf469ff87e3658d6";
    private static final String LEGACY_SALT = "user888";

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AppProperties appProperties;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private LoginAttemptGuard loginAttemptGuard;

    /**
     * 埋点门面。这里必须是 mock：登录链路会调用它，
     * 若注入 null 会在写埋点时抛 NPE，把「登录功能测试」变成「埋点测试」。
     */
    @Mock
    private BusinessMetrics businessMetrics;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        AppProperties.Jwt jwt = new AppProperties.Jwt();
        jwt.setExpireHours(72);
        when(appProperties.getJwt()).thenReturn(jwt);
        when(jwtUtil.generateToken(any(), any())).thenReturn("test-token");
    }

    private User userWith(String passwordHash, String salt) {
        User user = new User();
        user.setId(1L);
        user.setPhone(PHONE);
        user.setPassword(passwordHash);
        user.setSalt(salt);
        user.setNickname("张三");
        user.setStatus(1);
        return user;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setPhone(PHONE);
        request.setPassword(RAW_PASSWORD);
        request.setCaptchaKey("k");
        request.setCaptchaCode("1234");
        return request;
    }

    @Test
    @DisplayName("历史 MD5 账号登录成功后，该行被改写为 BCrypt 且盐清空")
    void legacyHashIsUpgradedOnLogin() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(userWith(LEGACY_HASH, LEGACY_SALT));

        LoginVO vo = userService.login(loginRequest());

        assertNotNull(vo.getToken(), "登录必须成功，升级过程对用户无感");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        User patch = captor.getValue();
        assertEquals(1L, patch.getId());
        assertTrue(PasswordUtil.isBcrypt(patch.getPassword()),
                "写入的密文应为 BCrypt，实际：" + patch.getPassword());
        assertEquals(PasswordUtil.NO_LEGACY_SALT, patch.getSalt());
        assertTrue(PasswordUtil.matches(RAW_PASSWORD, PasswordUtil.NO_LEGACY_SALT, patch.getPassword()),
                "新密文必须能用原密码校验通过");
    }

    @Test
    @DisplayName("已是 BCrypt 的账号登录不再产生任何写入")
    void bcryptHashIsNotRewritten() {
        when(userMapper.selectOne(any(Wrapper.class)))
                .thenReturn(userWith(PasswordUtil.encode(RAW_PASSWORD), PasswordUtil.NO_LEGACY_SALT));

        userService.login(loginRequest());

        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("密码错误时既不签令牌也不改写密文，并计入一次失败")
    void wrongPasswordDoesNotUpgrade() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(userWith(LEGACY_HASH, LEGACY_SALT));

        LoginRequest request = loginRequest();
        request.setPassword("wrong-password");

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        verify(userMapper, never()).updateById(any(User.class));
        // 失败锁定是登录安全的另一半：口令校验失败必须真正落到计数器上，否则等于没接
        verify(loginAttemptGuard).recordFailure("user", PHONE);
        verify(loginAttemptGuard, never()).reset(any(), any());
        // 埋点：失败必须计入指标，否则「登录失败率」这个最关键的监控项是空的
        verify(businessMetrics).loginAttempt("user", BusinessMetrics.RESULT_FAILURE);
    }

    @Test
    @DisplayName("密码正确时清零失败计数，避免零星输错累积成锁定")
    void successResetsFailureCounter() {
        when(userMapper.selectOne(any(Wrapper.class)))
                .thenReturn(userWith(PasswordUtil.encode(RAW_PASSWORD), PasswordUtil.NO_LEGACY_SALT));

        userService.login(loginRequest());

        verify(loginAttemptGuard).reset("user", PHONE);
        verify(loginAttemptGuard, never()).recordFailure(any(), any());
        verify(businessMetrics).loginAttempt("user", BusinessMetrics.RESULT_SUCCESS);
    }

    @Test
    @DisplayName("账号处于锁定期时直接拒绝，且不查库")
    void lockedAccountIsRejectedBeforeQuery() {
        doThrow(new BusinessException(ErrorCode.BIZ_ERROR, "登录失败次数过多，账号已被临时锁定，请 15 分钟后重试"))
                .when(loginAttemptGuard).checkNotLocked("user", PHONE);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(loginRequest()));

        assertEquals(ErrorCode.BIZ_ERROR, ex.getErrorCode());
        verify(userMapper, never()).selectOne(any(Wrapper.class));
        // 被锁定与口令错误的埋点结果分开记：失败量飙升但锁定量为 0，说明阈值还没起作用
        verify(businessMetrics).loginAttempt("user", BusinessMetrics.RESULT_LOCKED);
    }

    @Test
    @DisplayName("升级写入失败时登录仍然成功，不把用户挡在门外")
    void upgradeFailureDoesNotBreakLogin() {
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(userWith(LEGACY_HASH, LEGACY_SALT));
        when(userMapper.updateById(any(User.class))).thenThrow(new RuntimeException("数据库连接中断"));

        LoginVO vo = userService.login(loginRequest());

        assertNotNull(vo.getToken(), "写入失败只应记日志，登录本身必须照常返回令牌");
    }
}
