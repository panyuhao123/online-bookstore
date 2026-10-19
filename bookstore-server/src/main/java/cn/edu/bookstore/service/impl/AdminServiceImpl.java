package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.security.LoginAttemptGuard;
import cn.edu.bookstore.common.security.TokenRevocationService;
import cn.edu.bookstore.common.util.JwtUtil;
import cn.edu.bookstore.common.util.PasswordUtil;
import cn.edu.bookstore.config.AppProperties;
import cn.edu.bookstore.entity.Admin;
import cn.edu.bookstore.mapper.AdminMapper;
import cn.edu.bookstore.model.dto.AdminLoginRequest;
import cn.edu.bookstore.model.vo.AdminLoginVO;
import cn.edu.bookstore.model.vo.AdminVO;
import cn.edu.bookstore.service.AdminService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理员业务实现。
 *
 * <p>安全约定与 APP 用户一致：
 * <ul>
 *     <li>账号不存在、密码错误、账号被禁用分别处理，但对外文案不泄露账号是否存在；</li>
 *     <li>口令以 BCrypt 密文存储，历史 MD5(明文 + 盐) 数据在登录成功后透明升级；</li>
 *     <li>连续失败达阈值即临时锁定该账号（见 {@link LoginAttemptGuard}）——
 *         管理端登录没有图形验证码，这层是它唯一的在线防爆破手段，尤其重要；</li>
 *     <li>退出登录在服务端吊销令牌（见 {@link TokenRevocationService}）：
 *         管理端令牌权限大，不能只靠客户端删除；</li>
 *     <li>不提供「忘记密码/重置」流程，口令只能由超级管理员在库侧变更；</li>
 *     <li>日志只记 adminId，不记口令与盐值。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    /** 管理员角色标识，写入 JWT，JWT 过滤器据此区分 admin / user */
    private static final String ROLE_ADMIN = "admin";

    /** 登录失败计数的账号域，与 APP 用户（user）隔离 */
    private static final String SCOPE_ADMIN = "admin";

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final AppProperties appProperties;
    private final LoginAttemptGuard loginAttemptGuard;
    private final TokenRevocationService tokenRevocationService;

    @Override
    public AdminLoginVO login(AdminLoginRequest request) {
        // 1. 锁定检查：管理端没有验证码，被锁时直接拒绝，连数据库都不查
        loginAttemptGuard.checkNotLocked(SCOPE_ADMIN, request.getUsername());

        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, request.getUsername()));

        if (admin == null || !PasswordUtil.matches(request.getPassword(), admin.getSalt(), admin.getPassword())) {
            loginAttemptGuard.recordFailure(SCOPE_ADMIN, request.getUsername());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }

        // 口令正确即清零失败计数，避免若干天前的零星输错累积成「连续失败」被误锁
        loginAttemptGuard.reset(SCOPE_ADMIN, request.getUsername());

        if (Integer.valueOf(0).equals(admin.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系超级管理员");
        }

        // 哈希升级：与 APP 用户同一套策略，管理员账号同样不应长期停留在历史算法上
        upgradeHashIfNeeded(admin, request.getPassword());

        String token = jwtUtil.generateToken(admin.getId(), ROLE_ADMIN);
        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setExpireHours(appProperties.getJwt().getExpireHours());
        vo.setAdmin(AdminVO.from(admin));
        log.info("管理员登录成功 adminId={}", admin.getId());
        return vo;
    }

    /**
     * 历史哈希透明升级：库中密文还不是 BCrypt 时改写为 BCrypt。
     *
     * <p>升级失败只记日志不抛出，避免因一次写入异常影响管理员登录。
     */
    private void upgradeHashIfNeeded(Admin admin, String rawPassword) {
        if (!PasswordUtil.needsUpgrade(admin.getPassword())) {
            return;
        }
        try {
            Admin patch = new Admin();
            patch.setId(admin.getId());
            patch.setPassword(PasswordUtil.encode(rawPassword));
            patch.setSalt(PasswordUtil.NO_LEGACY_SALT);
            adminMapper.updateById(patch);
            log.info("已将 adminId={} 的密码哈希从历史算法升级为 BCrypt", admin.getId());
        } catch (Exception e) {
            log.warn("密码哈希升级失败，本次登录不受影响，adminId={}，原因：{}", admin.getId(), e.getMessage());
        }
    }

    @Override
    public void logout(String token) {
        // 管理端令牌可直接操作商品、订单与用户，退出登录必须在服务端作废它，
        // 否则令牌一旦被复制（共享运维电脑、浏览器历史、抓包），在过期前都能继续用。
        tokenRevocationService.revokeToken(token, ROLE_ADMIN);
    }

    @Override
    public AdminVO getInfo(Long adminId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        return AdminVO.from(admin);
    }
}
