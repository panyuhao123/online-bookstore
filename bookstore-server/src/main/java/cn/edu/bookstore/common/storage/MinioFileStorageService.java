package cn.edu.bookstore.common.storage;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * MinIO 对象存储实现（bookstore.storage.type=minio 时启用）。
 *
 * <p>相比本地磁盘的收益：图片与业务数据分离，应用可以多实例部署而封面依旧共享，
 * 也便于后续接入 CDN。
 *
 * <p>两个务实的设计：
 * <ul>
 *     <li><b>启动不因 MinIO 挂掉而失败</b>：初始化失败只告警并置为不可用，
 *         上传时再返回明确错误（提示如何启动 MinIO 或切回 local），
 *         避免「MinIO 没开导致整个后端起不来、连登录都进不去」；</li>
 *     <li><b>桶策略自动配置</b>：启动时确保 bucket 存在并设为公开读，
 *         这样封面 URL 可以直接在 APP、浏览器里访问，无需签名。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bookstore.storage.type", havingValue = "minio")
public class MinioFileStorageService implements FileStorageService {

    /** 公开读策略：允许匿名读取桶内对象，封面图才能被 APP 直接加载 */
    private static final String PUBLIC_READ_POLICY_TEMPLATE = """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": {"AWS": ["*"]},
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::%s/*"]
                }
              ]
            }
            """;

    private final AppProperties appProperties;

    private MinioClient client;

    /** 当前是否可用（初始化失败后，每次上传会重试一次，MinIO 起来后无需重启后端） */
    private volatile boolean available = false;

    @PostConstruct
    public void init() {
        available = tryInit();
    }

    @Override
    public String save(byte[] content, String originalFilename, String contentType, String dir) {
        if (!available && !tryInit()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "对象存储（MinIO）当前不可用，请启动 MinIO 或将 bookstore.storage.type 改为 local");
        }

        String ext = FileNameUtil.extractExt(originalFilename);
        String objectName = dir + "/" + FileNameUtil.generateName(ext);
        try (InputStream in = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectName)
                    .stream(in, content.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败 object={}", objectName, e);
            throw new BusinessException(ErrorCode.BIZ_ERROR, "封面上传到对象存储失败，请稍后重试");
        }

        String url = publicBaseUrl() + "/" + bucket() + "/" + objectName;
        log.info("MinIO 上传成功 url={} size={}", url, content.length);
        return url;
    }

    @Override
    public String type() {
        return "minio";
    }

    /** 建立连接并确保桶存在且可公开读取；失败返回 false（不抛异常，由上传时兜底提示） */
    private boolean tryInit() {
        try {
            AppProperties.Storage.Minio conf = appProperties.getStorage().getMinio();
            MinioClient newClient = MinioClient.builder()
                    .endpoint(conf.getEndpoint())
                    .credentials(conf.getAccessKey(), conf.getSecretKey())
                    .build();
            if (!newClient.bucketExists(BucketExistsArgs.builder().bucket(conf.getBucket()).build())) {
                newClient.makeBucket(MakeBucketArgs.builder().bucket(conf.getBucket()).build());
                log.info("MinIO 存储桶已创建 bucket={}", conf.getBucket());
            }
            newClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(conf.getBucket())
                    .config(String.format(PUBLIC_READ_POLICY_TEMPLATE, conf.getBucket()))
                    .build());
            client = newClient;
            log.info("MinIO 初始化完成 endpoint={} bucket={}", conf.getEndpoint(), conf.getBucket());
            return true;
        } catch (Exception e) {
            log.warn("MinIO 初始化失败（不影响服务启动，上传时将返回明确错误）：{}。"
                    + "可执行 D:\\minio\\start-minio.bat 启动，或把 bookstore.storage.type 改回 local",
                    e.getMessage());
            return false;
        }
    }

    private String bucket() {
        return appProperties.getStorage().getMinio().getBucket();
    }

    /** 对外访问前缀：留空时回落到 endpoint */
    private String publicBaseUrl() {
        String configured = appProperties.getStorage().getMinio().getPublicBaseUrl();
        String base = (configured == null || configured.isBlank())
                ? appProperties.getStorage().getMinio().getEndpoint()
                : configured;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
