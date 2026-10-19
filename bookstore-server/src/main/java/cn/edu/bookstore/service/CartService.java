package cn.edu.bookstore.service;

import cn.edu.bookstore.model.dto.CartAddRequest;
import cn.edu.bookstore.model.dto.CartMergeRequest;
import cn.edu.bookstore.model.dto.CartUpdateRequest;
import cn.edu.bookstore.model.vo.CartListVO;
import cn.edu.bookstore.model.vo.CartMergeResultVO;

/**
 * 购物车业务接口。所有方法都要求已登录，userId 由 Spring Security 认证后的登录上下文提供。
 *
 * @author 毕设项目组
 */
public interface CartService {

    /**
     * 合并未登录期间的本地购物车。
     *
     * <p>合并规则：与服务端已有条目「累加」而不是覆盖（用户本地加过的商品不该丢），
     * 数量受库存与单品上限约束；已下架或库存为 0 的商品跳过并在结果中说明原因。
     *
     * @param userId  当前登录用户
     * @param request 本地购物车内容
     * @return 合并结果（成功条数、总件数、跳过明细）
     */
    CartMergeResultVO merge(Long userId, CartMergeRequest request);

    /** 加入购物车：同一本书已存在则累加数量 */
    void add(Long userId, CartAddRequest request);

    /** 查询购物车 */
    CartListVO list(Long userId);

    /** 修改数量或勾选状态（需校验条目归属） */
    void update(Long userId, Long cartId, CartUpdateRequest request);

    /** 删除条目（需校验条目归属） */
    void remove(Long userId, Long cartId);

    /** 批量删除（APP 端「删除选中」） */
    void removeBatch(Long userId, java.util.List<Long> cartIds);
}
