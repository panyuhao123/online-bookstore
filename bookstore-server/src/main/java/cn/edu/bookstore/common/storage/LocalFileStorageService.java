package cn.edu.bookstore.common.storage;

import cn.edu.bookstore.config.AppProperties;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 本地磁盘存储实现（默认）。
 *
 * <p>文件落在 bookstore.upload.base-dir 下，通过 /files/** 静态映射对外提供访问。
 * 优点是零外部依赖、开箱即用；缺点是应用多实例部署时文件不共享 ——
 * 这正是引入 MinIO 的动机（见 {@link MinioFileStorageService}）。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bookstore.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final AppProperties appProperties;

    @Override
    public String save(byte[] content, String originalFilename, String contentType, String dir) {
        String ext = FileNameUtil.extractExt(originalFilename);
        String filename = FileNameUtil.generateName(ext);
        try {
            Path targetDir = Path.of(appProperties.getUpload().getBaseDir(), dir);
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(filename), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.error("本地存储写入失败 dir={}", dir, e);
            throw new IllegalStateException("文件保存失败，请稍后重试", e);
        }
        String url = appProperties.getUpload().getAccessPrefix() + "/" + dir + "/" + filename;
        log.info("本地存储上传成功 url={} size={}", url, content.length);
        return url;
    }

    @Override
    public String type() {
        return "local";
    }
}
