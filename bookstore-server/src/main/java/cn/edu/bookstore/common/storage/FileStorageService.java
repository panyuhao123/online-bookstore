package cn.edu.bookstore.common.storage;

/**
 * 文件存储抽象。
 *
 * <p>为什么抽接口：毕设环境里「本地磁盘」和「MinIO 对象存储」两种方式都要能用 ——
 * 演示时 MinIO 万一没起来，切一个配置就能回到本地存储，不影响答辩。
 * 对上层（FileController）而言只是一次 save 调用，不关心底层实现。
 *
 * @author 毕设项目组
 */
public interface FileStorageService {

    /**
     * 保存文件并返回可公网/局域网访问的 URL。
     *
     * @param content          文件字节内容
     * @param originalFilename 原始文件名（仅用于取扩展名，不参与最终命名，避免路径穿越）
     * @param contentType      MIME 类型，如 image/jpeg
     * @param dir              业务子目录，如 cover（本地模式为磁盘目录，MinIO 模式为对象前缀）
     * @return 可直接写入 book.cover_url 的地址
     */
    String save(byte[] content, String originalFilename, String contentType, String dir);

    /** 存储类型标识，用于日志与健康检查（local / minio） */
    String type();
}
