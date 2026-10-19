package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * <ul>
 *     <li>GET /health —— 存活探针：服务进程是否还活着；</li>
 *     <li>GET /ready —— 就绪探针：数据库是否可连通，用于启动后自检与联调排错。</li>
 * </ul>
 *
 * <p>真机联调第一步：手机浏览器访问 http://电脑IP:8080/ready 能返回 200，
 * 说明网络与数据库都通，再排查 APP 请求问题。
 *
 * @author 毕设项目组
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    /** 存活检查 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "bookstore-server");
        data.put("time", LocalDateTime.now().toString());
        return Result.success(data);
    }

    /** 就绪检查：验证数据库连通性 */
    @GetMapping("/ready")
    public Result<Map<String, Object>> ready() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            data.put("status", "UP");
            data.put("database", "UP");
        } catch (Exception e) {
            log.error("数据库连通性检查失败", e);
            return Result.fail(ErrorCode.INTERNAL_ERROR, "数据库不可用：" + e.getMessage());
        }
        data.put("time", LocalDateTime.now().toString());
        return Result.success(data);
    }
}
