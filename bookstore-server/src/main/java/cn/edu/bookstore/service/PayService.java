package cn.edu.bookstore.service;

import cn.edu.bookstore.model.vo.PrepayVO;

import java.util.Map;

/**
 * 支付服务抽象。
 *
 * <p>把「支付渠道」从订单业务里剥离出来，是这个模块唯一的设计目标。
 * 订单服务只关心「订单是否已支付」，不关心用户是用模拟支付还是支付宝付的；
 * 换渠道时只增一个实现类 + 改一行配置，业务代码不动。
 *
 * <p>与「文件存储 FileStorageService」是同一套思路 ——
 * 项目里凡是有多种实现、且实现细节与业务无关的能力，都抽成接口 + 配置切换。
 *
 * <p>已实现两个渠道：
 * <ul>
 *     <li>{@code mock}：模拟支付（默认），点一下即支付成功，答辩演示零外部依赖；</li>
 *     <li>{@code alipay}：支付宝沙箱，组装下单参数并做 RSA2 签名/验签，
 *         需要沙箱凭证（appId / 应用私钥 / 支付宝公钥）才能真正跑通。</li>
 * </ul>
 *
 * @author 毕设项目组
 */
public interface PayService {

    /**
     * 当前支付渠道标识（mock / alipay），用于前端展示与日志排查。
     */
    String type();

    /**
     * 发起支付：校验订单归属与状态，返回收银台所需信息。
     *
     * <p>对于模拟支付渠道，本方法会**直接完成支付**并返回 {@code paid = true}；
     * 对于支付宝渠道，只返回跳转地址（{@code paid = false}），
     * 真正的支付结果由支付宝异步通知由 {@link #handleNotify(Map)} 处理。
     *
     * @param userId  当前登录用户，用于校验订单归属
     * @param orderId 订单 ID
     * @return 收银台信息
     */
    PrepayVO prepay(Long userId, Long orderId);

    /**
     * 处理支付渠道的异步通知（回调）。
     *
     * <p>实现必须做两件事：
     * <ol>
     *     <li><b>验签</b>：确认请求确实来自支付渠道，而不是伪造的；</li>
     *     <li><b>幂等</b>：同一个通知可能被重复推送，重复处理不能造成订单被反复「支付」。</li>
     * </ol>
     *
     * <p>模拟支付渠道不涉及外部回调，实现为「不支持」。
     *
     * @param params 回调参数（支付宝以 form 表单方式 POST 过来）
     * @return 需要返回给支付渠道的响应内容（支付宝要求返回纯文本 success）
     */
    String handleNotify(Map<String, String> params);
}
