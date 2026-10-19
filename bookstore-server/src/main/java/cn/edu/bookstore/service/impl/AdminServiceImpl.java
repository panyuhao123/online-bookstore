package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
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
 *     <li>口令校验统一走 MD5(明文 + 盐)，不实现"忘记密码/重置"以外的流程；</li>
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

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;
    private final AppProperties appProperties;

    @Override
    public AdminLoginVO login(AdminLoginRequest request) {
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, request.getUsername()));

        if (admin == null || !PasswordUtil.matches(request.getPassword(), admin.getSalt(), admin.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (Integer.valueOf(0).equals(admin.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用，请联系超级管理员");
        }

        String token = jwtUtil.generateToken(admin.getId(), ROLE_ADMIN);
        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setExpireHours(appProperties.getJwt().getExpireHours());
        vo.setAdmin(AdminVO.from(admin));
        log.info("管理员登录成功 adminId={}", admin.getId());
        return vo;
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
