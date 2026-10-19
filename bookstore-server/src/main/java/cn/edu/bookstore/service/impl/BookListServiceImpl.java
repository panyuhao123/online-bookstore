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
import cn.edu.bookstore.model.dto.BookListRequest;
import cn.edu.bookstore.model.vo.BookListDetailVO;
import cn.edu.bookstore.model.vo.BookListVO;
import cn.edu.bookstore.model.vo.BookVO;
import cn.edu.bookstore.model.vo.PageVO;
import cn.edu.bookstore.service.BookListService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 书单业务实现。
 *
 * <p>两条贯穿全文的约定：
 * <ol>
 *     <li><b>不出现 N+1 查询</b>。列表页需要「创建者昵称」与「封面」，两者都不在 book_list 表里，
 *         一律走「先查本页书单 → 按 ID 批量查关联数据 → 内存组装」；
 *         {@link #fillCoverFromFirstBook} 与 {@link #fillOwner} 是这两步的公共实现。</li>
 *     <li><b>book_count 只在这里维护</b>。加书、移书、删书单三处都同步更新冗余计数，
 *         别处不允许直接写该字段，否则计数会静默漂移。</li>
 * </ol>
 *
 * @author 毕设项目组
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookListServiceImpl implements BookListService {

    /** 同一用户的书单数量上限：防止用脚本批量刷书单把表撑爆 */
    private static final int MAX_LISTS_PER_USER = 50;

    /** 单个书单的图书数量上限 */
    private static final int MAX_BOOKS_PER_LIST = 200;

    /** 平台治理状态：1-正常 */
    private static final int STATUS_NORMAL = 1;

    /** 平台治理状态：0-已隐藏（与用户自己的 is_public 无关） */
    private static final int STATUS_HIDDEN = 0;

    private final BookListMapper bookListMapper;
    private final BookListItemMapper bookListItemMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;

    @Override
    public PageVO<BookListVO> page(Long currentUserId, Long pageNum, Long pageSize, String keyword) {
        Long num = normalizePageNum(pageNum);
        Long size = normalizePageSize(pageSize);

        LambdaQueryWrapper<BookList> wrapper = new LambdaQueryWrapper<BookList>()
                // 广场只展示「平台未隐藏」的书单。这一条必须写在最外层与后续条件并列，
                // 而不能并进下面的 or 分组 —— 并进去会变成「公开 or 自己的」之后再 or 掉状态，
                // 被隐藏的书单就会从「自己创建」那一支漏出来。
                .eq(BookList::getStatus, STATUS_NORMAL)
                .and(w -> w
                        // 公开书单所有人可见
                        .eq(BookList::getIsPublic, 1)
                        // 自己的私密书单也能在广场里看到（否则用户建完就"找不到了"）
                        .or(currentUserId != null, inner -> inner.eq(BookList::getUserId, currentUserId)))
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(BookList::getName, keyword)
                        .or().like(BookList::getDescription, keyword))
                .orderByDesc(BookList::getCreateTime);

        IPage<BookList> page = bookListMapper.selectPage(new Page<>(num, size), wrapper);
        List<BookList> records = page.getRecords() == null ? List.of() : page.getRecords();

        List<BookListVO> vos = records.stream().map(BookListVO::from).toList();
        fillOwner(vos, currentUserId);
        fillCoverFromFirstBook(vos);
        return buildPage(page, vos);
    }

    @Override
    public PageVO<BookListVO> myLists(Long userId, Long pageNum, Long pageSize) {
        Long num = normalizePageNum(pageNum);
        Long size = normalizePageSize(pageSize);

        // 刻意**不**按 status 过滤：被平台隐藏的书单仍要在「我的书单」里可见。
        // 否则用户眼里的现象是「我建的书单凭空消失了」—— 既无从申诉，也像是数据丢失。
        // 正确做法是照常返回并带上 status=0，由前端渲染「该内容已被隐藏」的提示。
        LambdaQueryWrapper<BookList> wrapper = new LambdaQueryWrapper<BookList>()
                .eq(BookList::getUserId, userId)
                .orderByDesc(BookList::getCreateTime);

        IPage<BookList> page = bookListMapper.selectPage(new Page<>(num, size), wrapper);
        List<BookList> records = page.getRecords() == null ? List.of() : page.getRecords();

        List<BookListVO> vos = records.stream().map(BookListVO::from).toList();
        fillOwner(vos, userId);
        fillCoverFromFirstBook(vos);
        return buildPage(page, vos);
    }

    @Override
    public BookListDetailVO detail(Long currentUserId, Long listId) {
        BookList list = bookListMapper.selectById(listId);
        if (list == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "书单不存在或已被删除");
        }
        // 平台已隐藏的书单：对非创建者一律当作「不存在」。
        // 这里用 404 而不是 403 —— 隐藏的目的就是让内容不可见，
        // 若回 403 等于告诉访客「这个 ID 上有东西，只是不给你看」，隐藏就失效了。
        // 创建者本人仍可打开（能看见自己的内容、也知道发生了什么），
        // 与「我的书单」不过滤 status 是同一套口径。
        boolean hidden = Integer.valueOf(STATUS_HIDDEN).equals(list.getStatus());
        boolean owner = currentUserId != null && list.getUserId().equals(currentUserId);
        if (hidden && !owner) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "书单不存在或已被删除");
        }
        // 私密书单只对创建者开放。这里显式抛 403 而不是「当作不存在返回 404」：
        // 书单 ID 是自增的，返回 404 并不能真正隐藏它的存在，反而会让创建者
        // 在排查自己书单打不开时看到误导性的「不存在」。
        if (!Integer.valueOf(1).equals(list.getIsPublic()) && !owner) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该设为「仅自己可见」的书单只有创建者能查看");
        }

        BookListDetailVO vo = BookListDetailVO.of(list, loadBooks(list.getId()));
        List<BookListVO> single = List.of(vo);
        fillOwner(single, currentUserId);
        fillCoverFromFirstBook(single);
        vo.setOwnerNickname(single.get(0).getOwnerNickname());
        vo.setMine(single.get(0).getMine());
        if (StrUtil.isBlank(vo.getCoverUrl())) {
            vo.setCoverUrl(single.get(0).getCoverUrl());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookListVO create(Long userId, BookListRequest request) {
        long total = bookListMapper.selectCount(new LambdaQueryWrapper<BookList>()
                .eq(BookList::getUserId, userId));
        if (total >= MAX_LISTS_PER_USER) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "最多创建 " + MAX_LISTS_PER_USER + " 个书单，请先删除不再需要的书单");
        }
        requireNameAvailable(userId, request.getName(), null);

        BookList list = new BookList();
        list.setUserId(userId);
        list.setName(request.getName().trim());
        list.setDescription(request.getDescription());
        list.setCoverUrl(StrUtil.blankToDefault(request.getCoverUrl(), null));
        list.setIsPublic(Integer.valueOf(0).equals(request.getIsPublic()) ? 0 : 1);
        // 新书单必然为空，显式写 0 而不是依赖数据库默认值 —— 读代码时不必翻建表语句
        list.setBookCount(0);
        // 同理，新书单必然是正常状态。这里显式写 1 还有一个作用：
        // 让「status 只由管理端改变」这条规则在代码里看得见。
        list.setStatus(STATUS_NORMAL);
        bookListMapper.insert(list);

        log.info("新建书单 userId={} listId={} name={}", userId, list.getId(), list.getName());
        BookListVO vo = BookListVO.from(list);
        vo.setMine(true);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookListVO update(Long userId, Long listId, BookListRequest request) {
        BookList list = requireOwned(userId, listId);
        requireNameAvailable(userId, request.getName(), listId);

        list.setName(request.getName().trim());
        list.setDescription(request.getDescription());
        list.setCoverUrl(StrUtil.blankToDefault(request.getCoverUrl(), null));
        list.setIsPublic(Integer.valueOf(0).equals(request.getIsPublic()) ? 0 : 1);
        bookListMapper.updateById(list);

        BookListVO vo = BookListVO.from(list);
        vo.setMine(true);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long listId) {
        requireOwned(userId, listId);
        // 先删条目再删主表：虽然项目没建物理外键（业务外键在注释里声明），
        // 但顺序保持「先子后父」，将来真加了外键约束也不用改代码。
        bookListItemMapper.delete(new LambdaQueryWrapper<BookListItem>()
                .eq(BookListItem::getListId, listId));
        bookListMapper.deleteById(listId);
        log.info("删除书单 userId={} listId={}", userId, listId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addBooks(Long userId, Long listId, List<Long> bookIds) {
        BookList list = requireOwned(userId, listId);

        // 去重：前端一次提交里可能带上重复 ID（多选框重复点击），
        // 交给数据库唯一键会抛异常，不如在这里先规整掉。
        List<Long> distinctIds = bookIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return 0;
        }

        // 书单容量校验：把「已有 + 本次新增」一起算，避免分批提交绕过上限
        Map<Long, Book> bookMap = loadBookMap(distinctIds);
        long existing = bookListItemMapper.selectCount(new LambdaQueryWrapper<BookListItem>()
                .eq(BookListItem::getListId, listId));
        long toAdd = distinctIds.stream().filter(bookMap::containsKey).count();
        if (existing + toAdd > MAX_BOOKS_PER_LIST) {
            throw new BusinessException(ErrorCode.BIZ_ERROR,
                    "单个书单最多收录 " + MAX_BOOKS_PER_LIST + " 本图书");
        }

        // 已在书单中的图书先查出来，跳过它们而不是让唯一键抛错 ——
        // 「加 10 本、其中 3 本已在书单」是常见操作，不该整体失败。
        List<Long> alreadyIds = bookListItemMapper.selectList(new LambdaQueryWrapper<BookListItem>()
                        .select(BookListItem::getBookId)
                        .eq(BookListItem::getListId, listId))
                .stream().map(BookListItem::getBookId).toList();
        java.util.Set<Long> already = new java.util.HashSet<>(alreadyIds);

        int sort = (int) existing;
        int added = 0;
        int missing = 0;
        int skipped = 0;
        List<BookListItem> toInsert = new ArrayList<>();
        for (Long bookId : distinctIds) {
            if (!bookMap.containsKey(bookId)) {
                // 图书不存在：跳过而不是整体报错。前端传错一个 ID 不该让整批加书失败。
                missing++;
                continue;
            }
            if (already.contains(bookId)) {
                skipped++;
                continue;
            }
            BookListItem item = new BookListItem();
            item.setListId(listId);
            item.setBookId(bookId);
            item.setSort(sort++);
            toInsert.add(item);
            added++;
        }
        toInsert.forEach(bookListItemMapper::insert);

        if (added > 0) {
            syncBookCount(listId);
            log.info("书单加书 userId={} listId={} 新增={} 已在书单={} 图书不存在={}",
                    userId, listId, added, skipped, missing);
        }
        return added;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeBook(Long userId, Long listId, Long bookId) {
        requireOwned(userId, listId);
        int rows = bookListItemMapper.delete(new LambdaQueryWrapper<BookListItem>()
                .eq(BookListItem::getListId, listId)
                .eq(BookListItem::getBookId, bookId));
        if (rows > 0) {
            syncBookCount(listId);
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 校验书单存在且属于当前用户 */
    private BookList requireOwned(Long userId, Long listId) {
        if (listId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "书单 ID 不能为空");
        }
        BookList list = bookListMapper.selectById(listId);
        if (list == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "书单不存在或已被删除");
        }
        if (!list.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能编辑自己的书单");
        }
        return list;
    }

    /**
     * 校验同名书单。
     *
     * <p>同一用户不允许两个同名书单 —— 名字是用户唯一的识别依据，
     * 出现两个「想读」只会让人困惑。不同用户之间当然可以重名。
     *
     * @param excludeId 编辑场景下排除自身
     */
    private void requireNameAvailable(Long userId, String name, Long excludeId) {
        long count = bookListMapper.selectCount(new LambdaQueryWrapper<BookList>()
                .eq(BookList::getUserId, userId)
                .eq(BookList::getName, name.trim())
                .ne(excludeId != null, BookList::getId, excludeId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已有同名书单，请换一个名字");
        }
    }

    /** 加载书单内图书（按书单内顺序），保持与加书顺序一致 */
    private List<BookVO> loadBooks(Long listId) {
        List<BookListItem> items = bookListItemMapper.selectList(new LambdaQueryWrapper<BookListItem>()
                .eq(BookListItem::getListId, listId)
                .orderByAsc(BookListItem::getSort)
                .orderByAsc(BookListItem::getId));
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Book> bookMap = loadBookMap(items.stream().map(BookListItem::getBookId).toList());
        // 图书被物理删除时 bookMap 里没有它，这里直接跳过（列表里少一本总好过整个书单打不开）
        return items.stream()
                .map(item -> BookVO.from(bookMap.get(item.getBookId())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 按 ID 批量取图书，返回 ID -> 图书 */
    private Map<Long, Book> loadBookMap(List<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Book> books = bookMapper.selectBatchIds(bookIds);
        if (books == null || books.isEmpty()) {
            return Collections.emptyMap();
        }
        return books.stream().collect(Collectors.toMap(Book::getId, Function.identity(), (a, b) -> a));
    }

    /** 批量补创建者昵称与「是否本人」标记 */
    private void fillOwner(List<? extends BookListVO> vos, Long currentUserId) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> ownerIds = vos.stream().map(BookListVO::getOwnerId).distinct().toList();
        Map<Long, User> userMap = loadUserMap(ownerIds);
        for (BookListVO vo : vos) {
            User user = userMap.get(vo.getOwnerId());
            vo.setOwnerNickname(user == null ? "未知用户" : nicknameOf(user));
            vo.setMine(currentUserId != null && currentUserId.equals(vo.getOwnerId()));
        }
    }

    /**
     * 用书单内第一本书的封面补齐封面地址。
     *
     * <p>用户建书单时通常不会专门上传封面，若列表卡片一律显示占位图，
     * 整个书单广场会显得很空。用首本书的封面兜底既省去用户操作，视觉上也自然。
     * 实现上分两次批量查询完成（条目表 + 图书表），不随书单数量增长。
     */
    private void fillCoverFromFirstBook(List<? extends BookListVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> needIds = vos.stream()
                .filter(vo -> StrUtil.isBlank(vo.getCoverUrl()))
                .filter(vo -> vo.getBookCount() != null && vo.getBookCount() > 0)
                .map(BookListVO::getId)
                .toList();
        if (needIds.isEmpty()) {
            return;
        }

        List<BookListItem> items = bookListItemMapper.selectList(new LambdaQueryWrapper<BookListItem>()
                .select(BookListItem::getListId, BookListItem::getBookId, BookListItem::getSort)
                .in(BookListItem::getListId, needIds)
                .orderByAsc(BookListItem::getListId)
                .orderByAsc(BookListItem::getSort)
                .orderByAsc(BookListItem::getId));
        // putIfAbsent + 上面的排序 = 每个书单的第一本书
        Map<Long, Long> firstBookIdMap = new LinkedHashMap<>();
        for (BookListItem item : items) {
            firstBookIdMap.putIfAbsent(item.getListId(), item.getBookId());
        }
        if (firstBookIdMap.isEmpty()) {
            return;
        }
        Map<Long, Book> bookMap = loadBookMap(new ArrayList<>(firstBookIdMap.values()));
        for (BookListVO vo : vos) {
            if (StrUtil.isNotBlank(vo.getCoverUrl())) {
                continue;
            }
            Book book = bookMap.get(firstBookIdMap.get(vo.getId()));
            if (book != null && StrUtil.isNotBlank(book.getCoverUrl())) {
                vo.setCoverUrl(book.getCoverUrl());
            }
        }
    }

    /** 批量取用户，避免逐条查用户表 */
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
        String phone = user.getPhone();
        if (StrUtil.isBlank(phone) || phone.length() < 11) {
            return "书友";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 重算并回写冗余计数 */
    private void syncBookCount(Long listId) {
        long count = bookListItemMapper.selectCount(new LambdaQueryWrapper<BookListItem>()
                .eq(BookListItem::getListId, listId));
        bookListMapper.update(null, new LambdaUpdateWrapper<BookList>()
                .set(BookList::getBookCount, (int) count)
                .eq(BookList::getId, listId));
    }

    /** 用已组装好的 VO 列表构造分页结构（保持 MyBatis-Plus 的 total/pages 口径） */
    private PageVO<BookListVO> buildPage(IPage<BookList> page, List<BookListVO> vos) {
        PageVO<BookListVO> vo = new PageVO<>();
        vo.setList(vos);
        vo.setTotal(page.getTotal());
        vo.setPageNum(page.getCurrent());
        vo.setPageSize(page.getSize());
        vo.setPages(page.getPages());
        return vo;
    }

    private Long normalizePageNum(Long pageNum) {
        return (pageNum == null || pageNum < 1) ? 1L : pageNum;
    }

    private Long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 50L);
    }
}
