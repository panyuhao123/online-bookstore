-- MySQL dump 10.13  Distrib 8.0.17, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: bookstore
-- ------------------------------------------------------
-- Server version	8.0.17

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `address`
--

DROP TABLE IF EXISTS `address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `address` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID（业务外键 -> user.id）',
  `receiver` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '收件人姓名',
  `phone` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '联系电话',
  `province` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '省',
  `city` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '市',
  `district` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '区/县',
  `detail` varchar(255) COLLATE utf8mb4_general_ci NOT NULL COMMENT '详细地址',
  `is_default` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否默认地址：1-默认 0-普通',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_address_user` (`user_id`),
  KEY `idx_address_default` (`user_id`,`is_default`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收货地址表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `address`
--

LOCK TABLES `address` WRITE;
/*!40000 ALTER TABLE `address` DISABLE KEYS */;
INSERT INTO `address` VALUES (1,1,'张三','13800138000','广东省','深圳市','南山区','科技园南路 88 号 A 栋 1502',1,'2026-10-15 21:56:57','2026-10-16 10:52:01'),(4,4,'德','18251354688','北京市','北京市','东城区','无',1,'2026-10-16 17:37:11','2026-10-16 17:37:11');
/*!40000 ALTER TABLE `address` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `admin`
--

DROP TABLE IF EXISTS `admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '管理员账号（唯一）',
  `password` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码密文 = MD5(明文 + salt)',
  `salt` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码随机盐值',
  `real_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '真实姓名',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='管理员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin`
--

LOCK TABLES `admin` WRITE;
/*!40000 ALTER TABLE `admin` DISABLE KEYS */;
INSERT INTO `admin` VALUES (1,'admin','95719c0d6509e437a5182cd4c3e8cb73','admin888','系统管理员',1,'2026-10-15 21:56:57','2026-10-15 21:56:57');
/*!40000 ALTER TABLE `admin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `book`
--

DROP TABLE IF EXISTS `book`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `book` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_id` bigint(20) DEFAULT NULL COMMENT '所属分类ID（业务外键 -> category.id）',
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '书名',
  `author` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '作者',
  `publisher` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '出版社',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '单价',
  `stock` int(11) NOT NULL DEFAULT '0' COMMENT '库存数量',
  `cover_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '封面图地址',
  `description` text COLLATE utf8mb4_general_ci COMMENT '图书简介',
  `sales` int(11) NOT NULL DEFAULT '0' COMMENT '销量',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-上架 0-下架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_book_category` (`category_id`),
  KEY `idx_book_status` (`status`),
  KEY `idx_book_title` (`title`),
  KEY `idx_book_status_cate_price` (`status`,`category_id`,`price`),
  KEY `idx_book_sales` (`sales`),
  FULLTEXT KEY `ft_book_search` (`title`,`author`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='图书表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `book`
--

LOCK TABLES `book` WRITE;
/*!40000 ALTER TABLE `book` DISABLE KEYS */;
INSERT INTO `book` VALUES (1,1,'Java 编程思想（第4版）','Bruce Eckel','机械工业出版社',88.00,47,'/files/cover/java-thinking.jpg','Java 领域经典著作，从基础语法到高级特性系统讲解，适合有一定编程基础的读者深入学习。',123,1,'2026-10-15 21:56:57','2026-10-16 20:37:11'),(2,1,'Spring Boot 实战','Craig Walls','人民邮电出版社',69.00,40,'/files/cover/springboot.jpg','系统介绍 Spring Boot 核心特性与自动配置原理，包含大量可运行示例。',96,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(3,1,'深入理解计算机系统','Randal Bryant','机械工业出版社',139.00,30,'/files/cover/csapp.jpg','从程序员视角看计算机系统，涵盖处理器、存储器层次结构、链接与并发等核心主题。',88,1,'2026-10-15 21:56:57','2026-10-16 16:10:01'),(4,1,'算法导论（第3版）','Thomas Cormen','机械工业出版社',128.00,25,'/files/cover/algorithms.jpg','算法领域权威教材，内容严谨全面，是计算机专业学生与工程师的必备参考书。',75,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(5,1,'MySQL 是怎样运行的','小孩子4919','人民邮电出版社',79.00,60,'/files/cover/mysql.jpg','以通俗语言讲解 MySQL 的索引、事务、锁与查询优化，适合入门进阶。',64,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(6,2,'活着','余华','作家出版社',28.00,178,'/files/cover/live.jpg','以朴素笔触讲述福贵坎坷的一生，是中国当代文学中最具代表性的作品之一。',342,1,'2026-10-15 21:56:57','2026-10-16 22:46:09'),(7,2,'百年孤独','加西亚·马尔克斯','南海出版公司',45.00,149,'/files/cover/solitude.jpg','魔幻现实主义文学巅峰之作，描写布恩迪亚家族七代人的兴衰传奇。',281,1,'2026-10-15 21:56:57','2026-10-16 20:37:11'),(8,2,'平凡的世界','路遥','人民文学出版社',68.00,119,'/files/cover/ordinary.jpg','全景式展现中国当代城乡社会生活，获茅盾文学奖。',261,1,'2026-10-15 21:56:57','2026-10-16 20:37:11'),(9,2,'围城','钱钟书','人民文学出版社',32.00,180,'/files/cover/fortress.jpg','以幽默犀利的语言刻画知识分子的精神困境，现代文学经典。',190,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(10,3,'史记（全本）','司马迁','中华书局',96.00,40,'/files/cover/shiji.jpg','中国第一部纪传体通史，被列为二十四史之首。',85,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(11,3,'万历十五年','黄仁宇','中华书局',36.00,80,'/files/cover/wanli.jpg','以大历史观审视明朝社会结构，是明史研究的经典入门读物。',150,1,'2026-10-15 21:56:57','2026-10-16 16:10:01'),(12,3,'人类简史','尤瓦尔·赫拉利','中信出版社',68.00,99,'/files/cover/sapiens.jpg','从认知革命到现代社会，梳理人类文明发展的宏大脉络。',301,1,'2026-10-15 21:56:57','2026-10-16 20:37:11'),(13,3,'全球通史','斯塔夫里阿诺斯','北京大学出版社',88.00,50,'/files/cover/worldhistory.jpg','从全球视角叙述人类历史进程，是通史写作的典范之作。',110,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(14,4,'经济学原理','N.格里高利·曼昆','北京大学出版社',128.00,45,'/files/cover/economics.jpg','最受欢迎的经济学入门教材，体系清晰，案例贴近现实。',130,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(15,4,'国富论','亚当·斯密','商务印书馆',78.00,35,'/files/cover/wealth.jpg','古典经济学奠基之作，首次系统阐述分工与市场机制。',70,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(16,4,'穷爸爸富爸爸','罗伯特·清崎','萧氏出版社',38.00,160,'/files/cover/richdad.jpg','通过两种金钱观的对比，启发读者重新认识资产与负债。',240,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(17,4,'原则','瑞·达利欧','中信出版社',98.00,60,'/files/cover/principles.jpg','桥水基金创始人总结的生活与工作原则，兼具实操与思辨。',160,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(18,5,'心理学与生活','理查德·格里格','人民邮电出版社',88.00,54,'/files/cover/psychology.jpg','经典心理学教材，将科学研究与日常生活紧密结合。',141,1,'2026-10-15 21:56:57','2026-10-16 17:46:55'),(19,5,'思考，快与慢','丹尼尔·卡尼曼','中信出版社',69.00,70,'/files/cover/thinking.jpg','诺贝尔经济学奖得主剖析人类直觉与理性两套思维系统。',210,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(20,5,'被讨厌的勇气','岸见一郎','机械工业出版社',45.00,129,'/files/cover/courage.jpg','以对话体阐释阿德勒心理学，探讨如何获得真正的自由。',281,1,'2026-10-15 21:56:57','2026-10-16 20:37:11'),(21,5,'蛤蟆先生去看心理医生','罗伯特·戴博德','天津人民出版社',42.00,110,'/files/cover/toad.jpg','以童话式叙事讲心理咨询过程，通俗易懂的心理入门书。',230,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(22,6,'时间简史','史蒂芬·霍金','湖南科技出版社',45.00,88,'/files/cover/time.jpg','面向大众的宇宙学科普经典，探讨时间、黑洞与宇宙起源。',252,1,'2026-10-15 21:56:57','2026-10-16 20:37:11'),(23,6,'万物简史','比尔·布莱森','接力出版社',52.00,60,'/files/cover/short.jpg','用幽默笔触讲述科学发现史，涵盖宇宙、地球与生命演化。',120,1,'2026-10-15 21:56:57','2026-10-15 21:56:57'),(24,6,'上帝掷骰子吗','曹天元','北京联合出版公司',59.00,75,'/files/cover/quantum.jpg','以故事化方式梳理量子力学发展史，科普佳作。',170,1,'2026-10-15 21:56:57','2026-10-15 21:56:57');
/*!40000 ALTER TABLE `book` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cart`
--

DROP TABLE IF EXISTS `cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID（业务外键 -> user.id）',
  `book_id` bigint(20) NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
  `quantity` int(11) NOT NULL DEFAULT '1' COMMENT '购买数量',
  `checked` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否勾选：1-已选 0-未选',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_book` (`user_id`,`book_id`),
  KEY `idx_cart_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=82 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='购物车表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cart`
--

LOCK TABLES `cart` WRITE;
/*!40000 ALTER TABLE `cart` DISABLE KEYS */;
/*!40000 ALTER TABLE `cart` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `category`
--

DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类名称，如：文学 / 计算机 / 历史',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序值，越大越靠前',
  `remark` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`name`),
  KEY `idx_category_sort` (`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='图书分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `category`
--

LOCK TABLES `category` WRITE;
/*!40000 ALTER TABLE `category` DISABLE KEYS */;
INSERT INTO `category` VALUES (1,'计算机',100,'计算机与编程技术类图书','2026-10-15 21:56:57','2026-10-15 21:56:57'),(2,'文学',90,'小说、散文、诗歌等文学作品','2026-10-15 21:56:57','2026-10-15 21:56:57'),(3,'历史',80,'中外历史与人物传记','2026-10-15 21:56:57','2026-10-15 21:56:57'),(4,'经济',70,'经济、金融与管理','2026-10-15 21:56:57','2026-10-15 21:56:57'),(5,'心理学',60,'心理学与自我成长','2026-10-15 21:56:57','2026-10-15 21:56:57'),(6,'科普',50,'科学普及与通识读物','2026-10-15 21:56:57','2026-10-15 21:56:57');
/*!40000 ALTER TABLE `category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `operation_log`
--

DROP TABLE IF EXISTS `operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `admin_id` bigint(20) DEFAULT NULL COMMENT '鎿嶄綔绠＄悊鍛業D锛堜笟鍔″?閿?-> admin.id锛',
  `admin_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作人名称快照（管理员真实姓名；名称可能被修改，故同时保留 admin_id）',
  `module` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '涓氬姟妯″潡锛屽?銆屽浘涔︾?鐞嗐?',
  `action` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '鎿嶄綔鍔ㄤ綔锛屽?銆屾柊澧炲浘涔︺?',
  `http_method` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'HTTP 鏂规硶',
  `uri` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '璇锋眰璺?緞',
  `params` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '璇锋眰鍙傛暟锛堟晱鎰熷瓧娈靛凡鑴辨晱锛岃秴闀挎埅鏂?級',
  `ip` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '瀹㈡埛绔?IP',
  `duration_ms` bigint(20) DEFAULT NULL COMMENT '鑰楁椂锛堟?绉掞級',
  `success` tinyint(4) NOT NULL DEFAULT '1' COMMENT '鏄?惁鎴愬姛锛?-鎴愬姛 0-澶辫触',
  `error_msg` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '澶辫触鍘熷洜锛堜粎澶辫触鏃惰?褰曪級',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鎿嶄綔鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_oplog_create_time` (`create_time`),
  KEY `idx_oplog_module` (`module`),
  KEY `idx_oplog_admin` (`admin_id`)
) ENGINE=InnoDB AUTO_INCREMENT=128 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绠＄悊绔?搷浣滄棩蹇';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `operation_log`
--

LOCK TABLES `operation_log` WRITE;
/*!40000 ALTER TABLE `operation_log` DISABLE KEYS */;
INSERT INTO `operation_log` VALUES (1,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',8,1,NULL,'2026-10-16 16:12:21'),(2,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',4,1,NULL,'2026-10-16 16:12:21'),(3,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',4,1,NULL,'2026-10-16 16:12:21'),(4,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类8325\",\"sort\":1}]','127.0.0.1',12,1,NULL,'2026-10-16 16:12:21'),(5,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',3,0,'分类不存在','2026-10-16 16:12:21'),(6,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',4,1,NULL,'2026-10-16 16:13:18'),(7,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',31,1,NULL,'2026-10-16 16:13:19'),(8,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',4,1,NULL,'2026-10-16 16:13:19'),(9,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',5,1,NULL,'2026-10-16 16:13:19'),(10,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类3954\",\"sort\":1}]','127.0.0.1',14,1,NULL,'2026-10-16 16:13:19'),(11,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',3,0,'分类不存在','2026-10-16 16:13:19'),(12,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/10','[10]','127.0.0.1',16,1,NULL,'2026-10-16 16:13:20'),(13,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',4,1,NULL,'2026-10-16 16:13:38'),(14,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',3,1,NULL,'2026-10-16 16:13:39'),(15,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',4,1,NULL,'2026-10-16 16:13:39'),(16,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',4,1,NULL,'2026-10-16 16:13:39'),(17,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',3,1,NULL,'2026-10-16 16:13:39'),(18,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类2636\",\"sort\":1}]','127.0.0.1',8,1,NULL,'2026-10-16 16:13:39'),(19,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',4,0,'分类不存在','2026-10-16 16:13:39'),(20,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/11','[11]','127.0.0.1',13,1,NULL,'2026-10-16 16:13:40'),(21,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',50,1,NULL,'2026-10-16 16:16:24'),(22,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',5,1,NULL,'2026-10-16 16:16:24'),(23,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',20,1,NULL,'2026-10-16 16:16:24'),(24,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',5,1,NULL,'2026-10-16 16:16:24'),(25,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',5,1,NULL,'2026-10-16 16:16:24'),(26,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类8842\",\"sort\":1}]','127.0.0.1',15,1,NULL,'2026-10-16 16:16:24'),(27,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',4,0,'分类不存在','2026-10-16 16:16:24'),(28,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/12','[12]','127.0.0.1',13,1,NULL,'2026-10-16 16:16:26'),(29,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/14/ship','[14]','127.0.0.1',12,1,NULL,'2026-10-16 16:28:45'),(30,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/15/ship','[15]','127.0.0.1',7,1,NULL,'2026-10-16 16:28:45'),(31,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/1/status','[1,0]','127.0.0.1',15,1,NULL,'2026-10-16 16:28:45'),(32,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/1/status','[1,0]','127.0.0.1',5,1,NULL,'2026-10-16 16:28:46'),(33,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/1/status','[1,2]','127.0.0.1',1,0,'状态只能是 1（正常）或 0（隐藏）','2026-10-16 16:28:46'),(34,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/1/status','[1,1]','127.0.0.1',12,1,NULL,'2026-10-16 16:28:46'),(35,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/1','[1]','127.0.0.1',11,1,NULL,'2026-10-16 16:28:46'),(36,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/99999999','[99999999]','127.0.0.1',2,0,'评价不存在','2026-10-16 16:28:46'),(37,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/2','[2]','127.0.0.1',8,1,NULL,'2026-10-16 16:28:46'),(38,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/18/ship','[18]','127.0.0.1',10,1,NULL,'2026-10-16 16:34:17'),(39,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/19/ship','[19]','127.0.0.1',8,1,NULL,'2026-10-16 16:34:18'),(40,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/3/status','[3,0]','127.0.0.1',16,1,NULL,'2026-10-16 16:34:18'),(41,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/3/status','[3,0]','127.0.0.1',4,1,NULL,'2026-10-16 16:34:18'),(42,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/3/status','[3,2]','127.0.0.1',2,0,'状态只能是 1（正常）或 0（隐藏）','2026-10-16 16:34:18'),(43,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/3/status','[3,1]','127.0.0.1',9,1,NULL,'2026-10-16 16:34:18'),(44,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/3','[3]','127.0.0.1',9,1,NULL,'2026-10-16 16:34:18'),(45,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/99999999','[99999999]','127.0.0.1',1,0,'评价不存在','2026-10-16 16:34:18'),(46,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/4','[4]','127.0.0.1',7,1,NULL,'2026-10-16 16:34:19'),(47,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/22/ship','[22]','127.0.0.1',7,1,NULL,'2026-10-16 16:34:27'),(48,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/23/ship','[23]','127.0.0.1',6,1,NULL,'2026-10-16 16:34:27'),(49,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/5/status','[5,0]','127.0.0.1',10,1,NULL,'2026-10-16 16:34:27'),(50,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/5/status','[5,0]','127.0.0.1',3,1,NULL,'2026-10-16 16:34:27'),(51,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/5/status','[5,2]','127.0.0.1',1,0,'状态只能是 1（正常）或 0（隐藏）','2026-10-16 16:34:27'),(52,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/5/status','[5,1]','127.0.0.1',9,1,NULL,'2026-10-16 16:34:28'),(53,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/5','[5]','127.0.0.1',8,1,NULL,'2026-10-16 16:34:28'),(54,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/99999999','[99999999]','127.0.0.1',1,0,'评价不存在','2026-10-16 16:34:28'),(55,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/6','[6]','127.0.0.1',9,1,NULL,'2026-10-16 16:34:28'),(56,1,'系统管理员','文件管理','上传封面','POST','/api/admin/upload','[\"<StandardMultipartFile>\"]','127.0.0.1',27,1,NULL,'2026-10-16 16:38:39'),(57,1,'系统管理员','文件管理','上传封面','POST','/api/admin/upload','[\"<StandardMultipartFile>\"]','127.0.0.1',1,0,'仅支持上传图片文件','2026-10-16 16:38:39'),(58,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/27/ship','[27]','127.0.0.1',8,1,NULL,'2026-10-16 16:38:49'),(59,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/28/ship','[28]','127.0.0.1',7,1,NULL,'2026-10-16 16:38:49'),(60,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/7/status','[7,0]','127.0.0.1',10,1,NULL,'2026-10-16 16:38:49'),(61,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/7/status','[7,0]','127.0.0.1',4,1,NULL,'2026-10-16 16:38:49'),(62,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/7/status','[7,2]','127.0.0.1',2,0,'状态只能是 1（正常）或 0（隐藏）','2026-10-16 16:38:49'),(63,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/7/status','[7,1]','127.0.0.1',8,1,NULL,'2026-10-16 16:38:49'),(64,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/7','[7]','127.0.0.1',7,1,NULL,'2026-10-16 16:38:49'),(65,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/99999999','[99999999]','127.0.0.1',1,0,'评价不存在','2026-10-16 16:38:49'),(66,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/8','[8]','127.0.0.1',7,1,NULL,'2026-10-16 16:38:49'),(67,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',33,1,NULL,'2026-10-16 16:38:59'),(68,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',4,1,NULL,'2026-10-16 16:38:59'),(69,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',13,1,NULL,'2026-10-16 16:38:59'),(70,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',2,1,NULL,'2026-10-16 16:38:59'),(71,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',3,1,NULL,'2026-10-16 16:38:59'),(72,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类1938\",\"sort\":1}]','127.0.0.1',9,1,NULL,'2026-10-16 16:38:59'),(73,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',2,0,'分类不存在','2026-10-16 16:38:59'),(74,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/13','[13]','127.0.0.1',9,1,NULL,'2026-10-16 16:39:00'),(75,1,'系统管理员','文件管理','上传封面','POST','/api/admin/upload','[\"<StandardMultipartFile>\"]','127.0.0.1',15,1,NULL,'2026-10-16 16:40:45'),(76,1,'系统管理员','文件管理','上传封面','POST','/api/admin/upload','[\"<StandardMultipartFile>\"]','127.0.0.1',1,0,'仅支持上传图片文件','2026-10-16 16:40:46'),(77,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/33/ship','[33]','127.0.0.1',6,1,NULL,'2026-10-16 16:40:50'),(78,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/34/ship','[34]','127.0.0.1',11,1,NULL,'2026-10-16 16:40:50'),(79,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/9/status','[9,0]','127.0.0.1',11,1,NULL,'2026-10-16 16:40:51'),(80,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/9/status','[9,0]','127.0.0.1',3,1,NULL,'2026-10-16 16:40:51'),(81,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/9/status','[9,2]','127.0.0.1',3,0,'状态只能是 1（正常）或 0（隐藏）','2026-10-16 16:40:51'),(82,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/9/status','[9,1]','127.0.0.1',8,1,NULL,'2026-10-16 16:40:51'),(83,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/9','[9]','127.0.0.1',8,1,NULL,'2026-10-16 16:40:51'),(84,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/99999999','[99999999]','127.0.0.1',1,0,'评价不存在','2026-10-16 16:40:51'),(85,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/10','[10]','127.0.0.1',9,1,NULL,'2026-10-16 16:40:51'),(86,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',17,1,NULL,'2026-10-16 16:40:53'),(87,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',2,1,NULL,'2026-10-16 16:40:53'),(88,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',13,1,NULL,'2026-10-16 16:40:53'),(89,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',3,1,NULL,'2026-10-16 16:40:54'),(90,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',3,1,NULL,'2026-10-16 16:40:54'),(91,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类1967\",\"sort\":1}]','127.0.0.1',9,1,NULL,'2026-10-16 16:40:54'),(92,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',1,0,'分类不存在','2026-10-16 16:40:54'),(93,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/14','[14]','127.0.0.1',10,1,NULL,'2026-10-16 16:40:55'),(94,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',4,1,NULL,'2026-10-16 16:42:32'),(95,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',3,1,NULL,'2026-10-16 16:42:32'),(96,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',10,1,NULL,'2026-10-16 16:42:32'),(97,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',1,1,NULL,'2026-10-16 16:42:32'),(98,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',2,1,NULL,'2026-10-16 16:42:32'),(99,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类9677\",\"sort\":1}]','127.0.0.1',8,1,NULL,'2026-10-16 16:42:32'),(100,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',1,0,'分类不存在','2026-10-16 16:42:32'),(101,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/15','[15]','127.0.0.1',12,1,NULL,'2026-10-16 16:42:33'),(102,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/11','[11]','127.0.0.1',23,1,NULL,'2026-10-16 19:15:53'),(103,1,'系统管理员','文件管理','上传封面','POST','/api/admin/upload','[\"<StandardMultipartFile>\"]','127.0.0.1',23,1,NULL,'2026-10-16 22:12:29'),(104,1,'系统管理员','文件管理','上传封面','POST','/api/admin/upload','[\"<StandardMultipartFile>\"]','127.0.0.1',1,0,'仅支持上传图片文件','2026-10-16 22:12:29'),(105,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/42/ship','[42]','127.0.0.1',8,1,NULL,'2026-10-16 22:12:32'),(106,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/43/ship','[43]','127.0.0.1',6,1,NULL,'2026-10-16 22:12:32'),(107,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/12/status','[12,0]','127.0.0.1',9,1,NULL,'2026-10-16 22:12:33'),(108,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/12/status','[12,0]','127.0.0.1',3,1,NULL,'2026-10-16 22:12:33'),(109,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/12/status','[12,2]','127.0.0.1',2,0,'状态只能是 1（正常）或 0（隐藏）','2026-10-16 22:12:33'),(110,1,'系统管理员','评价管理','变更评价状态','PUT','/api/admin/review/12/status','[12,1]','127.0.0.1',6,1,NULL,'2026-10-16 22:12:33'),(111,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/12','[12]','127.0.0.1',9,1,NULL,'2026-10-16 22:12:33'),(112,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/99999999','[99999999]','127.0.0.1',2,0,'评价不存在','2026-10-16 22:12:33'),(113,1,'系统管理员','评价管理','删除评价','DELETE','/api/admin/review/13','[13]','127.0.0.1',43,1,NULL,'2026-10-16 22:12:33'),(114,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',17,1,NULL,'2026-10-16 22:12:36'),(115,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout',NULL,'127.0.0.1',3,1,NULL,'2026-10-16 22:12:36'),(116,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',9,1,NULL,'2026-10-16 22:12:36'),(117,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[0]','127.0.0.1',2,1,NULL,'2026-10-16 22:12:36'),(118,1,'系统管理员','订单管理','手动取消超时订单','POST','/api/admin/order/cancel-timeout','[1440]','127.0.0.1',3,1,NULL,'2026-10-16 22:12:36'),(119,1,'系统管理员','分类管理','新增分类','POST','/api/admin/category','[{\"name\":\"测试分类3064\",\"sort\":1}]','127.0.0.1',10,1,NULL,'2026-10-16 22:12:36'),(120,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/99999999','[99999999]','127.0.0.1',3,0,'分类不存在','2026-10-16 22:12:36'),(121,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/16','[16]','127.0.0.1',14,1,NULL,'2026-10-16 22:12:38'),(122,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/40/ship','[40]','127.0.0.1',19,1,NULL,'2026-10-16 22:44:36'),(123,1,'系统管理员','图书管理','修改图书','PUT','/api/admin/book/6','[6,{\"categoryId\":2,\"title\":\"活着\",\"author\":\"余华\",\"publisher\":\"作家出版社\",\"price\":28,\"stock\":178,\"coverUrl\":\"/files/cover/live.jpg\",\"description\":\"以朴素笔触讲述福贵坎坷的一生，是中国当代文学中最具代表性的作品之一。\",\"status\":1}]','127.0.0.1',15,1,NULL,'2026-10-16 22:45:41'),(124,1,'系统管理员','图书管理','上下架图书','PUT','/api/admin/book/6/status','[6,0]','127.0.0.1',10,1,NULL,'2026-10-16 22:46:08'),(125,1,'系统管理员','图书管理','上下架图书','PUT','/api/admin/book/6/status','[6,1]','127.0.0.1',9,1,NULL,'2026-10-16 22:46:09'),(126,1,'系统管理员','分类管理','删除分类','DELETE','/api/admin/category/9','[9]','127.0.0.1',15,1,NULL,'2026-10-16 22:49:06'),(127,1,'系统管理员','订单管理','订单发货','POST','/api/admin/order/38/ship','[38]','127.0.0.1',19,1,NULL,'2026-10-16 23:46:48');
/*!40000 ALTER TABLE `operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_item`
--

DROP TABLE IF EXISTS `order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID（业务外键 -> orders.id）',
  `book_id` bigint(20) NOT NULL COMMENT '图书ID（业务外键 -> book.id）',
  `book_title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '书名快照',
  `book_cover` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '封面快照',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '下单时单价快照',
  `quantity` int(11) NOT NULL DEFAULT '1' COMMENT '购买数量',
  `subtotal` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '小计金额 = price * quantity',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order` (`order_id`),
  KEY `idx_order_item_book` (`book_id`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_item`
--

LOCK TABLES `order_item` WRITE;
/*!40000 ALTER TABLE `order_item` DISABLE KEYS */;
INSERT INTO `order_item` VALUES (1,1,1,'Java 编程思想（第4版）','/files/cover/java-thinking.jpg',88.00,2,176.00,'2026-10-16 10:05:46'),(2,1,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 10:05:46'),(3,2,3,'深入理解计算机系统','/files/cover/csapp.jpg',139.00,1,139.00,'2026-10-16 10:48:08'),(4,2,11,'万历十五年','/files/cover/wanli.jpg',36.00,2,72.00,'2026-10-16 10:48:08'),(5,3,6,'活着','/files/cover/live.jpg',28.00,2,56.00,'2026-10-16 15:21:45'),(6,4,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 15:21:46'),(7,5,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 15:28:27'),(8,6,6,'活着','/files/cover/live.jpg',28.00,2,56.00,'2026-10-16 15:42:50'),(9,7,6,'活着','/files/cover/live.jpg',28.00,2,56.00,'2026-10-16 15:48:38'),(10,8,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:12:21'),(11,9,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:13:18'),(12,10,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:13:39'),(13,11,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:16:24'),(14,12,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:28:43'),(15,13,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:28:44'),(16,14,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:28:45'),(17,15,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:28:45'),(18,16,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:15'),(19,17,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:17'),(20,18,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:17'),(21,19,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:18'),(22,20,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:25'),(23,21,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:27'),(24,22,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:27'),(25,23,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:34:27'),(26,24,6,'活着','/files/cover/live.jpg',28.00,2,56.00,'2026-10-16 16:38:40'),(27,25,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:38:47'),(28,26,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:38:48'),(29,27,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:38:49'),(30,28,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:38:49'),(31,29,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:38:59'),(32,30,6,'活着','/files/cover/live.jpg',28.00,2,56.00,'2026-10-16 16:40:46'),(33,31,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:40:48'),(34,32,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:40:50'),(35,33,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:40:50'),(36,34,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:40:50'),(37,35,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:40:53'),(38,36,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 16:42:32'),(39,37,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 17:46:55'),(40,37,18,'心理学与生活','/files/cover/psychology.jpg',88.00,1,88.00,'2026-10-16 17:46:55'),(41,38,1,'Java 编程思想（第4版）','/files/cover/java-thinking.jpg',88.00,1,88.00,'2026-10-16 20:37:11'),(42,38,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 20:37:11'),(43,38,7,'百年孤独','/files/cover/solitude.jpg',45.00,1,45.00,'2026-10-16 20:37:11'),(44,38,8,'平凡的世界','/files/cover/ordinary.jpg',68.00,1,68.00,'2026-10-16 20:37:11'),(45,38,12,'人类简史','/files/cover/sapiens.jpg',68.00,1,68.00,'2026-10-16 20:37:11'),(46,38,20,'被讨厌的勇气','/files/cover/courage.jpg',45.00,1,45.00,'2026-10-16 20:37:11'),(47,38,22,'时间简史','/files/cover/time.jpg',45.00,2,90.00,'2026-10-16 20:37:11'),(48,39,6,'活着','/files/cover/live.jpg',28.00,2,56.00,'2026-10-16 22:12:30'),(49,40,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 22:12:31'),(50,41,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 22:12:32'),(51,42,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 22:12:32'),(52,43,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 22:12:32'),(53,44,6,'活着','/files/cover/live.jpg',28.00,1,28.00,'2026-10-16 22:12:36');
/*!40000 ALTER TABLE `order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '订单编号（唯一，前端展示用）',
  `user_id` bigint(20) NOT NULL COMMENT '下单用户ID（业务外键 -> user.id）',
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '订单总金额',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '订单状态：1-待支付 2-已支付 3-已发货 4-已完成 5-已取消',
  `address_snapshot` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '收货地址快照：收件人 / 电话 / 完整地址',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间（模拟支付写入）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_no` (`order_no`),
  KEY `idx_orders_user` (`user_id`),
  KEY `idx_orders_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,'BK202610161005462494',1,204.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 10:05:48','2026-10-16 10:05:46','2026-10-16 14:44:01'),(2,'BK202610161048076774',1,211.00,5,'测试收货 13600136000 北京市北京市海淀区中关村大街 1 号',NULL,'2026-10-16 10:48:08','2026-10-16 16:10:01'),(3,'BK202610161521457082',1,56.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 15:21:45','2026-10-16 15:21:45'),(4,'BK202610161521457784',1,28.00,2,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 15:21:46','2026-10-16 15:21:46','2026-10-16 15:21:46'),(5,'BK202610161528277415',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 15:28:27','2026-10-16 15:28:27'),(6,'BK202610161542499809',1,56.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 15:42:50','2026-10-16 15:42:49'),(7,'BK202610161548388621',1,56.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 15:48:38','2026-10-16 15:48:38'),(8,'BK202610161612208404',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:12:21','2026-10-16 16:13:18'),(9,'BK202610161613184211',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:13:18','2026-10-16 16:13:18'),(10,'BK202610161613386076',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:13:39','2026-10-16 16:16:23'),(11,'BK202610161616233888',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:16:24','2026-10-16 16:16:23'),(12,'BK202610161628429396',1,28.00,2,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:28:43','2026-10-16 16:28:43','2026-10-16 16:28:43'),(13,'BK202610161628444825',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:28:44','2026-10-16 16:38:58'),(14,'BK202610161628443516',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:28:45','2026-10-16 16:28:45','2026-10-16 16:28:45'),(15,'BK202610161628459510',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:28:45','2026-10-16 16:28:45','2026-10-16 16:28:45'),(16,'BK202610161634153139',1,28.00,2,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:34:16','2026-10-16 16:34:15','2026-10-16 16:34:15'),(17,'BK202610161634177364',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:34:17','2026-10-16 16:38:58'),(18,'BK202610161634177028',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:34:17','2026-10-16 16:34:17','2026-10-16 16:34:17'),(19,'BK202610161634178115',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:34:18','2026-10-16 16:34:18','2026-10-16 16:34:18'),(20,'BK202610161634241014',1,28.00,2,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:34:25','2026-10-16 16:34:25','2026-10-16 16:34:25'),(21,'BK202610161634261112',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:34:27','2026-10-16 16:38:58'),(22,'BK202610161634268469',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:34:27','2026-10-16 16:34:27','2026-10-16 16:34:27'),(23,'BK202610161634264764',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:34:27','2026-10-16 16:34:27','2026-10-16 16:34:27'),(24,'BK202610161638406931',1,56.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:38:40','2026-10-16 16:38:40'),(25,'BK202610161638461075',1,28.00,2,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:38:47','2026-10-16 16:38:47','2026-10-16 16:38:47'),(26,'BK202610161638482832',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:38:48','2026-10-16 16:38:58'),(27,'BK202610161638488269',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:38:49','2026-10-16 16:38:49','2026-10-16 16:38:49'),(28,'BK202610161638486448',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:38:49','2026-10-16 16:38:49','2026-10-16 16:38:49'),(29,'BK202610161638582627',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:38:59','2026-10-16 16:38:58'),(30,'BK202610161640467689',1,56.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:40:46','2026-10-16 16:40:46'),(31,'BK202610161640482599',1,28.00,2,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:40:48','2026-10-16 16:40:48','2026-10-16 16:40:48'),(32,'BK202610161640493766',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:40:50','2026-10-16 16:40:53'),(33,'BK202610161640507715',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:40:50','2026-10-16 16:40:50','2026-10-16 16:40:50'),(34,'BK202610161640509072',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 16:40:50','2026-10-16 16:40:50','2026-10-16 16:40:50'),(35,'BK202610161640534197',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:40:53','2026-10-16 16:40:53'),(36,'BK202610161642319843',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 16:42:32','2026-10-16 16:42:31'),(37,'BK202610161746552770',4,116.00,2,'德 18251354688 北京市北京市东城区无','2026-10-16 17:47:01','2026-10-16 17:46:55','2026-10-16 17:46:55'),(38,'BK202610162037112010',4,432.00,3,'德 18251354688 北京市北京市东城区无','2026-10-16 20:37:16','2026-10-16 20:37:11','2026-10-16 23:46:48'),(39,'BK202610162212297212',1,56.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 22:12:30','2026-10-16 22:12:29'),(40,'BK202610162212308472',1,28.00,3,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 22:12:31','2026-10-16 22:12:31','2026-10-16 22:44:36'),(41,'BK202610162212316969',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 22:12:32','2026-10-16 22:12:35'),(42,'BK202610162212329738',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 22:12:32','2026-10-16 22:12:32','2026-10-16 22:12:32'),(43,'BK202610162212321567',1,28.00,4,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502','2026-10-16 22:12:32','2026-10-16 22:12:32','2026-10-16 22:12:32'),(44,'BK202610162212357389',1,28.00,5,'张三 13800138000 广东省深圳市南山区科技园南路 88 号 A 栋 1502',NULL,'2026-10-16 22:12:36','2026-10-16 22:12:36');
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `review`
--

DROP TABLE IF EXISTS `review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `review` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `book_id` bigint(20) NOT NULL COMMENT '鍥句功ID锛堜笟鍔″?閿?-> book.id锛',
  `user_id` bigint(20) NOT NULL COMMENT '璇勪环鐢ㄦ埛ID锛堜笟鍔″?閿?-> user.id锛',
  `order_id` bigint(20) NOT NULL COMMENT '鏉ユ簮璁㈠崟ID锛屼繚璇併?鍙?湁涔拌繃鎵嶈兘璇勩?',
  `rating` tinyint(4) NOT NULL COMMENT '鏄熺骇璇勫垎 1~5',
  `content` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '璇勪环鍐呭?',
  `anonymous` tinyint(4) NOT NULL DEFAULT '0' COMMENT '鏄?惁鍖垮悕锛?-鍖垮悕 0-鏄剧ず鏄电О',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '鐘舵?锛?-姝ｅ父 0-宸查殣钘忥紙鍚庡彴涓嬫灦杩濊?璇勪环锛',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '璇勪环鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user_book_order` (`user_id`,`book_id`,`order_id`),
  KEY `idx_review_book_status_time` (`book_id`,`status`,`create_time`),
  KEY `idx_review_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='鍥句功璇勪环';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `review`
--

LOCK TABLES `review` WRITE;
/*!40000 ALTER TABLE `review` DISABLE KEYS */;
/*!40000 ALTER TABLE `review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `phone` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '手机号（登录账号，唯一）',
  `password` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码密文 = MD5(明文 + salt)',
  `salt` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码随机盐值',
  `nickname` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户昵称',
  `avatar_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像地址',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  KEY `idx_user_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='APP 用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'13800138000','58ab4bd28431ad3abf469ff87e3658d6','user888','张三',NULL,1,'2026-10-15 21:56:57','2026-10-16 15:18:08'),(4,'18251354608','978a56729f844e4c903080f14d28f576','5a97bdfa','李',NULL,1,'2026-10-16 13:37:28','2026-10-16 14:44:01');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-17 11:24:06
