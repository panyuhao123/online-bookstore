package cn.edu.bookstore.service.impl;

import cn.edu.bookstore.common.BusinessException;
import cn.edu.bookstore.common.ErrorCode;
import cn.edu.bookstore.entity.Book;
import cn.edu.bookstore.entity.BookList;
import cn.edu.bookstore.entity.BookListItem;
import cn.edu.bookstore.entity.User;
import cn.edu.bookstore.mapper.BookListItemMapper;
import cn.edu.bookstore.mapper.BookListMapper;
import cn.edu.bookstore.mapper.BookMapper;
import cn.edu.bookstore.mapper.UserMapper;
import cn.edu.bookstore.model.dto.AdminBookListQuery;
import cn.edu.bookstore.model.vo.AdminBookListDetailVO;
import cn.edu.bookstore.model.vo.AdminBookListVO;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.AdminBookListService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 书单治理实现（管理端）。
 *
 * <p>三点须知：
 * <ol>
 *     <li><b>书单没有缓存</b>，因此隐藏/删除后不需要 {@code @CacheEvict} ——
 *         这条差异是刻意的：书单列表带分页与多维筛选，缓存命中率低而失效面很大，
 *         贸然加缓存反而容易出现「后台隐藏了、前台还在」的错觉。
 *         代价是每次都要查库，但书单列表本就是低频页面，可以接受。</li>
 *     <li><b>治理不动 is_public</b>。is_public 是用户自己的选择，隐藏只作用于平台展示。
 *         如果把隐藏实现成「把 is_public 改成 0」，恢复时就没法还原用户原本的设置了。</li>
 *     <li><b>删除必须先清条目再删主表</b>。项目没有建物理外键（业务外键写在注释里），
 *         顺序颠倒不会报错，但会留下永远没人认领的孤儿条目行。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBookListServiceImpl implements AdminBookListService {

    /** 治理状态：1-正常 */
    private static final int STATUS_NORMAL = 1;

    /** 治理状态：0-已隐藏 */
    private static final int STATUS_HIDDEN = 0;

    private final BookListMapper bookListMapper;
    private final BookListItemMapper bookListItemMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;

    @Override
    public PageVO<AdminBookListVO> page(AdminBookListQuery query) {
        AdminBookListQuery q = query == null ? new AdminBookListQuery() : query;
        q.normalize();

        LambdaQueryWrapper<BookList> wrapper = new LambdaQueryWrapper<BookList>()
                .eq(q.getStatus() != null, BookList::getStatus, q.getStatus())
                .eq(q.getUserId() != null, BookList::getUserId, q.getUserId())
                .eq(q.getIsPublic() != null, BookList::getIsPublic, q.getIsPublic())
                .and(StrUtil.isNotBlank(q.getKeyword()), w -> w
                        .like(BookList::getName, q.getKeyword().trim())
                        .or().like(BookList::getDescription, q.getKeyword().trim()))
                // 排序带 id 兜底：同一秒创建的两个书单若只按时间排，翻页时顺序可能不稳定
                .orderByDesc(BookList::getCreateTime)
                .orderByDesc(BookList::getId);

        IPage<BookList> page = bookListMapper.selectPage(
                new Page<>(q.getPageNum(), q.getPageSize()), wrapper);
        List<BookList> records = page.getRecords() == null ? List.of() : page.getRecords();
        List<AdminBookListVO> vos = records.stream().map(AdminBookListVO::from).toList();
        fillOwner(vos);

        PageVO<AdminBookListVO> vo = new PageVO<>();
        vo.setList(vos);
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }

    @Override
    public AdminBookListDetailVO detail(Long id) {
        BookList list = requireList(id);
        AdminBookListDetailVO vo = AdminBookListDetailVO.of(list, loadBooks(list.getId()));
        // 复用列表那套装配，保证昵称/手机号的兜底口径完全一致
        List<AdminBookListVO> single = List.of(vo);
        fillOwner(single);
        vo.setOwnerNickname(single.get(0).getOwnerNickname());
        vo.setOwnerPhone(single.get(0).getOwnerPhone());
        return vo;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (!Integer.valueOf(STATUS_HIDDEN).equals(status) && !Integer.valueOf(STATUS_NORMAL).equals(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只能是 1（正常）或 0（隐藏）");
        }
        BookList list = requireList(id);
        if (status.equals(list.getStatus())) {
            // 幂等：重复点「隐藏」不该产生无意义的 UPDATE，也不该写一条误导性的操作日志
            return;
        }
        BookList update = new BookList();
        update.setId(id);
        update.setStatus(status);
        bookListMapper.updateById(update);
        log.info("书单状态变更 listId={} ownerId={} status={}", id, list.getUserId(), status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BookList list = requireList(id);
        int items = bookListItemMapper.delete(new LambdaQueryWrapper<BookListItem>()
                .eq(BookListItem::getListId, id));
        bookListMapper.deleteById(id);
        log.warn("书单被删除 listId={} ownerId={} name={} 条目数={}",
                id, list.getUserId(), list.getName(), items);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 取出书单并校验存在；不存在即 404，避免对错误 ID 静默成功 */
    private BookList requireList(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "书单 ID 不能为空");
        }
        BookList list = bookListMapper.selectById(id);
        if (list == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "书单不存在或已被删除");
        }
        return list;
    }

    /** 批量补创建者昵称与脱敏手机号（一次查库，不随书单数量增长） */
    private void fillOwner(List<AdminBookListVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> ownerIds = vos.stream()
                .map(AdminBookListVO::getOwnerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = loadUserMap(ownerIds);
        for (AdminBookListVO vo : vos) {
            User user = userMap.get(vo.getOwnerId());
            vo.setOwnerNickname(user == null ? "未知用户" : nicknameOf(user));
            vo.setOwnerPhone(maskPhone(user == null ? null : user.getPhone()));
            // 后台不存在「我的书单」概念：管理员对任何书单都只是旁观者
            vo.setMine(false);
        }
    }

    /**
     * 加载条目明细。
     *
     * <p>与前台 {@code BookListServiceImpl#loadBooks} 逻辑相同但刻意不复用：
     * 那一份是 private 且服务于「前台可见性」语境，后台这份不需要任何可见性过滤
     * （下架图书也要照常展示，管理员才能判断内容是否违规）。为省二十行代码把两套
     * 语义拧在一起，将来前台加了过滤规则就会顺带把后台也改掉，得不偿失。
     */
    private List<BookVO> loadBooks(Long listId) {
        List<BookListItem> items = bookListItemMapper.selectList(new LambdaQueryWrapper<BookListItem>()
                .eq(BookListItem::getListId, listId)
                .orderByAsc(BookListItem::getSort)
                .orderByAsc(BookListItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> bookIds = items.stream().map(BookListItem::getBookId).toList();
        List<Book> books = bookMapper.selectBatchIds(bookIds);
        if (books == null || books.isEmpty()) {
            return List.of();
        }
        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, Function.identity(), (a, b) -> a));
        // 图书被物理删除时 bookMap 里没有它，跳过即可（少一本总好过整个详情打不开）
        return items.stream()
                .map(item -> BookVO.from(bookMap.get(item.getBookId())))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 批量取用户，返回 ID -> 用户 */
    private Map<Long, User> loadUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }
        return users.stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    /** 昵称兜底：没设昵称时用脱敏手机号，与项目其他位置的展示口径一致 */
    private String nicknameOf(User user) {
        if (StrUtil.isNotBlank(user.getNickname())) {
            return user.getNickname();
        }
        String masked = maskPhone(user.getPhone());
        return masked == null ? "书友" : masked;
    }

    /**
     * 手机号脱敏：13800138000 -> 138****8000。
     *
     * <p>后台确实需要能对上账号，但列表页铺满完整号码没有意义，
     * 还会把「看一眼后台」变成一次用户隐私泄露。
     */
    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
