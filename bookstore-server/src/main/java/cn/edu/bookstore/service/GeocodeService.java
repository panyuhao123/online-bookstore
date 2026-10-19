package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.GeocodeResult;

/**
 * 地址自动解析服务。
 *
 * <p>后端统一入口 —— 调高德地理编码 API，做签名、限流、异常兜底。
 *
 * @author 毕设项目组
 */
public interface GeocodeService {

    /**
     * 解析地址文本为省市区 + adcode + 候选列表。
     *
     * @param userId  当前登录用户（用于限流）
     * @param address 用户输入的完整地址文本
     * @return 候选地址列表（至少 1 条）
     */
    GeocodeResult parse(Long userId, String address);
}
