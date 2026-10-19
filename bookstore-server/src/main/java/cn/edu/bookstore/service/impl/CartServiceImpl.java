package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.Cart;
import cn.edu.bookstore.mapper.CartMapper;
import cn.edu.bookstore.model.dto.CartAddRequest;
import cn.edu.bookstore.model.dto.CartMergeRequest;
import cn.edu.bookstore.model.dto.CartUpdateRequest;
import cn.edu.bookstore.model.vo.CartListVO;
import cn.edu.bookstore.model.vo.CartMergeResultVO;
import cn.edu.bookstore.model.vo.CartVO;
import cn.edu.bookstore.service.BookService;
import cn.edu.bookstore.service.CartService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车业务实现。
 *
 * <p>关键规则：
 * <ul>
 *     <li>同一用户 + 同一本书只保留一条记录，重复加入时累加数量（与表唯一索引一致）；</li>
 *     <li>所有写操作都先校验条目归属当前用户，防止越权改他人购物车；</li>
 *     <li>数量上限受图书库存约束。</li>
 * </ul>
 *
 * <p>Redis 缓存策略（Cache-Aside，<b>MySQL 为唯一数据源</b>）：
 * <ul>
 *     <li><b>读</b>：{@link #list(Long)} 按用户缓存整个购物车视图（键 cart::{userId}），
 *         命中则直接返回，省掉「查购物车表 + 批量查图书」两次数据库往返；</li>
 *     <li><b>写</b>：所有写操作（加购 / 改数量 / 勾选 / 删除 / 合并）以及下单清空购物车，
 *         都会<b>失效</b>该用户的缓存，下次读取时从数据库重建。</li>
 * </ul>
 *
 * <p>为什么用「写失效」而不是「写时同时更新 Redis 与 MySQL」：
 * 双写要同时保证两边成功，Redis 写失败就会出现缓存与库不一致，
 * 还得额外处理补偿；而失效是幂等的 —— 多失效一次只是少一次命中，
 * 绝不会让用户看到错误数据。这是本项目的取舍：<b>一致性优先于缓存命中率</b>。
 *
 * <p>已知边界：缓存的 TTL 为 5 分钟（见 CacheConfig），这意味着别人下单导致库存变化后，
 * 本用户最多可能看到 5 分钟前的「库存充足」状态。真正的库存校验发生在下单时刻
 * （条件更新防超卖），所以不会出现超卖，只是提示信息可能略滞后。
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final int STATUS_ON_SALE = 1;
    private static final int MAX_QUANTITY = 99;

    /** 购物车缓存名，与 CacheConfig 中的配置保持一致 */
    private static final String CART_CACHE = "cart";

    private final CartMapper cartMapper;
    private final BookService bookService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CART_CACHE, key = "#userId")
    public CartMergeResultVO merge(Long userId, CartMergeRequest request) {
        // 1. 先把同一本书的数量合并到一起：APP 本地购物车可能因为多次加购产生重复条目
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CartMergeRequest.Item item : request.getItems()) {
            quantities.merge(item.getBookId(), item.getQuantity(), Integer::sum);
        }

        // 2. 批量查图书（一次 SQL），避免逐条校验
        Map<Long, Book> bookMap = bookService.mapByIds(new ArrayList<>(quantities.keySet()));

        // 3. 一次性取出该用户已有的购物车条目，按 bookId 建索引
        Map<Long, Cart> existedMap = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                        .eq(Cart::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(Cart::getBookId, cart -> cart, (a, b) -> a));

        CartMergeResultVO result = new CartMergeResultVO();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Long bookId = entry.getKey();
            int addQuantity = entry.getValue();
            Book book = bookMap.get(bookId);

            if (book == null || !Integer.valueOf(STATUS_ON_SALE).equals(book.getStatus())) {
                result.addSkipped(bookId, book == null ? null : book.getTitle(), "已下架或不存在");
                continue;
            }

            Cart existed = existedMap.get(bookId);
            int target = (existed == null ? 0 : existed.getQuantity()) + addQuantity;
            // 库存不足时不是整条丢弃，而是收敛到库存上限 —— 用户想买 3 件只剩 1 件时，
            // 把能买的那件留下来比全部丢掉更符合预期
            if (target > Math.min(book.getStock(), MAX_QUANTITY)) {
                target = Math.min(book.getStock(), MAX_QUANTITY);
                if (target <= 0) {
                    result.addSkipped(bookId, book.getTitle(), "库存不足");
                    continue;
                }
                result.addSkipped(bookId, book.getTitle(), "数量已调整为上限 " + target + " 件");
            }

            if (existed == null) {
                Cart cart = new Cart();
                cart.setUserId(userId);
                cart.setBookId(bookId);
                cart.setQuantity(target);
                cart.setChecked(1);
                cartMapper.insert(cart);
            } else {
                Cart update = new Cart();
                update.setId(existed.getId());
                update.setQuantity(target);
                update.setChecked(1);
                cartMapper.updateById(update);
            }
            result.setMergedCount(result.getMergedCount() + 1);
        }

        result.setTotalQuantity(totalQuantityOf(userId));
        log.info("本地购物车合并完成 userId={} 提交={} 成功={} 跳过={}",
                userId, quantities.size(), result.getMergedCount(), result.getSkipped().size());
        return result;
    }

    /** 合并后购物车的商品总件数，用于 APP 端提示「已同步 N 件商品」 */
    private int totalQuantityOf(Long userId) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .select(Cart::getQuantity)
                .eq(Cart::getUserId, userId));
        return carts.stream().mapToInt(cart -> cart.getQuantity() == null ? 0 : cart.getQuantity()).sum();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CART_CACHE, key = "#userId")
    public void add(Long userId, CartAddRequest request) {
        Book book = requireOnSaleBook(request.getBookId());
        int addQuantity = request.getQuantity() == null ? 1 : request.getQuantity();

        Cart existing = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getBookId, book.getId()));

        int finalQuantity = (existing == null ? 0 : existing.getQuantity()) + addQuantity;
        if (finalQuantity > book.getStock()) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "库存不足，当前仅剩 " + book.getStock() + " 件");
        }
        if (finalQuantity > MAX_QUANTITY) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "单个商品最多购买 " + MAX_QUANTITY + " 件");
        }

        if (existing == null) {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setBookId(book.getId());
            cart.setQuantity(addQuantity);
            cart.setChecked(1);
            cartMapper.insert(cart);
        } else {
            existing.setQuantity(finalQuantity);
            existing.setChecked(1);
            cartMapper.updateById(existing);
        }
    }

    @Override
    @Cacheable(value = CART_CACHE, key = "#userId")
    public CartListVO list(Long userId) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime));

        CartListVO result = new CartListVO();
        if (carts == null || carts.isEmpty()) {
            result.setList(Collections.emptyList());
            result.setCheckedAmount(BigDecimal.ZERO);
            result.setCheckedCount(0);
            return result;
        }

        // 一次性取出所有涉及图书，避免逐条查询
        Map<Long, Book> bookMap = bookService.mapByIds(
                carts.stream().map(Cart::getBookId).distinct().toList());

        BigDecimal checkedAmount = BigDecimal.ZERO;
        int checkedCount = 0;
        List<CartVO> list = new java.util.ArrayList<>(carts.size());
        for (Cart cart : carts) {
            Book book = bookMap.get(cart.getBookId());
            CartVO vo = new CartVO();
            vo.setId(cart.getId());
            vo.setBookId(cart.getBookId());
            vo.setQuantity(cart.getQuantity());
            vo.setChecked(cart.getChecked());
            if (book != null) {
                vo.setTitle(book.getTitle());
                vo.setCoverUrl(book.getCoverUrl());
                vo.setPrice(book.getPrice());
                vo.setStock(book.getStock());
                vo.setSubtotal(book.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
                vo.setStockEnough(book.getStock() >= cart.getQuantity());
                // 下架图书不计入合计，APP 端提示用户移除
                if (Integer.valueOf(1).equals(cart.getChecked())
                        && Integer.valueOf(STATUS_ON_SALE).equals(book.getStatus())
                        && book.getStock() >= cart.getQuantity()) {
                    checkedAmount = checkedAmount.add(vo.getSubtotal());
                    checkedCount += cart.getQuantity();
                }
            } else {
                vo.setStockEnough(false);
            }
            list.add(vo);
        }
        result.setList(list);
        result.setCheckedAmount(checkedAmount);
        result.setCheckedCount(checkedCount);
        return result;
    }

    @Override
    @CacheEvict(value = CART_CACHE, key = "#userId")
    public void update(Long userId, Long cartId, CartUpdateRequest request) {
        Cart cart = requireOwnedCart(userId, cartId);

        if (request.getQuantity() != null) {
            Book book = requireOnSaleBook(cart.getBookId());
            if (request.getQuantity() > book.getStock()) {
                throw new BusinessException(ErrorCode.BIZ_ERROR,
                        "库存不足，当前仅剩 " + book.getStock() + " 件");
            }
            cart.setQuantity(request.getQuantity());
        }
        if (request.getChecked() != null) {
            cart.setChecked(request.getChecked() == 0 ? 0 : 1);
        }
        cartMapper.updateById(cart);
    }

    @Override
    @CacheEvict(value = CART_CACHE, key = "#userId")
    public void remove(Long userId, Long cartId) {
        requireOwnedCart(userId, cartId);
        cartMapper.deleteById(cartId);
    }

    @Override
    @CacheEvict(value = CART_CACHE, key = "#userId")
    public void removeBatch(Long userId, List<Long> cartIds) {
        if (cartIds == null || cartIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先选择要删除的商品");
        }
        int deleted = cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .in(Cart::getId, cartIds));
        log.info("批量删除购物车条目 userId={} 请求={} 实际删除={}", userId, cartIds.size(), deleted);
    }

    /** 校验图书存在且上架 */
    private Book requireOnSaleBook(Long bookId) {
        Book book = bookService.mapByIds(List.of(bookId)).get(bookId);
        if (book == null || !Integer.valueOf(STATUS_ON_SALE).equals(book.getStatus())) {
            throw new BusinessException(ErrorCode.BIZ_ERROR, "图书不存在或已下架");
        }
        return book;
    }

    /** 校验购物车条目存在且属于当前用户（防止越权） */
    private Cart requireOwnedCart(Long userId, Long cartId) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "购物车条目不存在");
        }
        if (!cart.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人的购物车");
        }
        return cart;
    }
}
