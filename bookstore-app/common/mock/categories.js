/**
 * 图书分类（脱敏真实数据）
 * 一级 9 类，二级 48 类；id 规则：一级 1~9，二级 = 一级*100 + 序号
 *
 * 本文件由 tools/build_mock_data.py 生成，请勿手工编辑；
 * 修改数据请改 tools/mock_catalog.py 后重新运行生成脚本。
 */
export const CATEGORIES = [
  {"id": 1, "key": "fiction", "name": "小说", "sort": 100, "remark": "中外小说、悬疑推理与科幻文学", "children": [{"id": 101, "name": "中国当代小说", "sort": 1}, {"id": 102, "name": "古典名著", "sort": 2}, {"id": 103, "name": "悬疑推理", "sort": 3}, {"id": 104, "name": "科幻小说", "sort": 4}, {"id": 105, "name": "世界文学", "sort": 5}, {"id": 106, "name": "青春文学", "sort": 6}]},
  {"id": 2, "key": "literature", "name": "文学", "sort": 90, "remark": "散文、诗歌、文学理论与纪实作品", "children": [{"id": 201, "name": "散文随笔", "sort": 1}, {"id": 202, "name": "诗歌词曲", "sort": 2}, {"id": 203, "name": "文学理论", "sort": 3}, {"id": 204, "name": "名家文集", "sort": 4}, {"id": 205, "name": "纪实文学", "sort": 5}]},
  {"id": 3, "key": "children", "name": "少儿", "sort": 80, "remark": "绘本、儿童文学与启蒙读物", "children": [{"id": 301, "name": "绘本图画书", "sort": 1}, {"id": 302, "name": "儿童文学", "sort": 2}, {"id": 303, "name": "科普启蒙", "sort": 3}, {"id": 304, "name": "国学经典", "sort": 4}, {"id": 305, "name": "益智游戏", "sort": 5}]},
  {"id": 4, "key": "history", "name": "历史", "sort": 70, "remark": "通史、传记、考古与历史随笔", "children": [{"id": 401, "name": "中国通史", "sort": 1}, {"id": 402, "name": "世界历史", "sort": 2}, {"id": 403, "name": "人物传记", "sort": 3}, {"id": 404, "name": "考古文明", "sort": 4}, {"id": 405, "name": "历史随笔", "sort": 5}]},
  {"id": 5, "key": "business", "name": "经管", "sort": 60, "remark": "经济、投资、管理与职场成长", "children": [{"id": 501, "name": "经济理论", "sort": 1}, {"id": 502, "name": "投资理财", "sort": 2}, {"id": 503, "name": "企业管理", "sort": 3}, {"id": 504, "name": "市场营销", "sort": 4}, {"id": 505, "name": "职场成长", "sort": 5}]},
  {"id": 6, "key": "computer", "name": "计算机", "sort": 50, "remark": "编程、数据库、算法与人工智能", "children": [{"id": 601, "name": "编程语言", "sort": 1}, {"id": 602, "name": "数据库", "sort": 2}, {"id": 603, "name": "算法与数据结构", "sort": 3}, {"id": 604, "name": "人工智能", "sort": 4}, {"id": 605, "name": "网络与系统", "sort": 5}, {"id": 606, "name": "软件工程", "sort": 6}]},
  {"id": 7, "key": "education", "name": "教辅", "sort": 40, "remark": "中小学教辅、考研与语言学习", "children": [{"id": 701, "name": "中小学教辅", "sort": 1}, {"id": 702, "name": "考研辅导", "sort": 2}, {"id": 703, "name": "外语学习", "sort": 3}, {"id": 704, "name": "考试认证", "sort": 4}, {"id": 705, "name": "高校教材", "sort": 5}]},
  {"id": 8, "key": "art", "name": "艺术", "sort": 30, "remark": "绘画、摄影、音乐、设计与建筑", "children": [{"id": 801, "name": "绘画书法", "sort": 1}, {"id": 802, "name": "摄影摄像", "sort": 2}, {"id": 803, "name": "音乐舞蹈", "sort": 3}, {"id": 804, "name": "设计创意", "sort": 4}, {"id": 805, "name": "建筑园林", "sort": 5}]},
  {"id": 9, "key": "life", "name": "生活", "sort": 20, "remark": "健康、美食、心理、育儿与科普", "children": [{"id": 901, "name": "健康养生", "sort": 1}, {"id": 902, "name": "美食烹饪", "sort": 2}, {"id": 903, "name": "心理自助", "sort": 3}, {"id": 904, "name": "育儿家教", "sort": 4}, {"id": 905, "name": "旅行户外", "sort": 5}, {"id": 906, "name": "科普读物", "sort": 6}]}
]
