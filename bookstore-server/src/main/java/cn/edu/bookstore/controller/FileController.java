package cn.edu.bookstore.controller;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.common.Result;
import cn.edu.bookstore.common.log.OpLog;
import cn.edu.bookstore.common.storage.FileStorageService;
import cn.edu.bookstore.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 管理端文件上传（图书封面）。
 *
 * <p>实现要点：
 * <ul>
 *     <li>真正的落盘动作委托给 {@link FileStorageService}：本地磁盘与 MinIO 两套实现按配置切换，
 *         Controller 不关心文件最终存在哪里；</li>
 *     <li>校验四道关：文件非空、Content-Type 必须是图片、扩展名白名单、大小不超过配置上限
 *         （前端也校验一遍，但服务端校验才是安全边界 —— 前端校验可被绕过）；</li>
 *     <li>文件名由服务端统一生成（时间戳 + 随机串），避免同名覆盖与路径穿越；</li>
 *     <li>返回值中带上 storage 字段，便于前端与排查时确认当前用的是哪种存储。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class FileController {

    /** 允许的图片扩展名 */
    private static final List<String> ALLOWED_EXT = List.of("jpg", "jpeg", "png", "webp");

    private final AppProperties appProperties;
    private final FileStorageService fileStorageService;

    /**
     * 上传封面。
     *
     * <p>返回 { "url": "...", "storage": "local|minio" }，url 可直接写入 book.cover_url。
     */
    @OpLog(module = "文件管理", action = "上传封面")
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的图片");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持上传图片文件");
        }
        String ext = resolveExt(file.getOriginalFilename());
        if (ext == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "图片格式不支持，仅支持 " + String.join(" / ", ALLOWED_EXT));
        }
        long maxBytes = appProperties.getUpload().getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "图片不能超过 " + appProperties.getUpload().getMaxSizeMb() + "MB");
        }

        String url = fileStorageService.save(
                file.getBytes(), file.getOriginalFilename(), file.getContentType(), "cover");
        return Result.success(Map.of("url", url, "storage", fileStorageService.type()));
    }

    /** 提取小写扩展名；不在白名单内返回 null */
    private String resolveExt(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return null;
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        return ALLOWED_EXT.contains(ext) ? ext : null;
    }
}
