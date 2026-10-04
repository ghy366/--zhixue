-- ============================================================
-- 智学选课平台 - MySQL 初始化脚本
-- 执行方式: mysql -u root -p < init-mysql.sql
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS zhixue
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE zhixue;

-- ============================================================
-- 1. 用户表
-- ============================================================
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS system_config;

CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    no          VARCHAR(50)  NOT NULL COMMENT '学号/工号/管理员账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名',
    role        VARCHAR(20)  NOT NULL COMMENT 'student / teacher / admin',
    college     VARCHAR(100) DEFAULT NULL COMMENT '学院',
    major       VARCHAR(100) DEFAULT NULL COMMENT '专业',
    grade       INT          DEFAULT NULL COMMENT '年级',
    PRIMARY KEY (id),
    UNIQUE KEY uk_no (no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================================
-- 2. 课程表
-- ============================================================
CREATE TABLE courses (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL COMMENT '课程名',
    teacher_id  BIGINT       DEFAULT NULL COMMENT '授课老师ID',
    credit      INT          DEFAULT NULL COMMENT '学分',
    capacity    INT          DEFAULT NULL COMMENT '容量',
    category    VARCHAR(50)  DEFAULT NULL COMMENT '公共必修/专业必修/专业选修/公共选修',
    time        VARCHAR(200) DEFAULT NULL COMMENT '上课时间',
    location    VARCHAR(100) DEFAULT NULL COMMENT '上课地点',
    intro       VARCHAR(1000) DEFAULT NULL COMMENT '课程简介',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

-- ============================================================
-- 3. 选课记录表
-- ============================================================
CREATE TABLE enrollments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    student_id  BIGINT      NOT NULL COMMENT '学生ID',
    course_id   BIGINT      NOT NULL COMMENT '课程ID',
    round       INT         NOT NULL COMMENT '选课轮次 1/2/3',
    points      INT         NOT NULL COMMENT '投入积分',
    status      VARCHAR(20) NOT NULL COMMENT 'pending / approved / rejected / dropped',
    PRIMARY KEY (id),
    KEY idx_student (student_id),
    KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选课记录表';

-- ============================================================
-- 4. 系统配置表
-- ============================================================
CREATE TABLE system_config (
    id            BIGINT NOT NULL DEFAULT 1 COMMENT '单例固定ID=1',
    current_round INT    NOT NULL DEFAULT 1 COMMENT '当前选课轮次',
    round_open    TINYINT NOT NULL DEFAULT 1 COMMENT '是否开放选课 1=开放 0=关闭',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 系统配置
INSERT INTO system_config (id, current_round, round_open) VALUES (1, 1, 1);

-- 用户 (10条)
INSERT INTO users (no, password, name, role, college, major, grade) VALUES
('2023010101', '123456', '林晓宇', 'student', '计算机学院', '软件工程',       2023),
('2023010102', '123456', '苏雨桐', 'student', '计算机学院', '计算机科学与技术', 2023),
('2023010103', '123456', '陈昊然', 'student', '计算机学院', '软件工程',       2023),
('2023010104', '123456', '王思琪', 'student', '信息学院',   '信息安全',       2023),
('2023010105', '123456', '李梓涵', 'student', '计算机学院', '软件工程',       2023),
('T001',       '123456', '王建国', 'teacher', '数学学院',   NULL,             NULL),
('T002',       '123456', '赵明',   'teacher', '计算机学院', NULL,             NULL),
('T003',       '123456', '郑昊',   'teacher', '计算机学院', NULL,             NULL),
('T004',       '123456', '黄丽',   'teacher', '计算机学院', NULL,             NULL),
('admin',      'admin',  '管理员', 'admin',   '教务处',     NULL,             NULL);

-- 课程 (8条)
-- teacher_id 对应上面用户的 id: T001=6, T002=7, T003=8, T004=9
INSERT INTO courses (name, teacher_id, credit, capacity, category, time, location, intro) VALUES
('高等数学(下)',     6, 4, 120, '公共必修', '周一 08:00-09:40 / 周三 10:00-11:40', '教1-201',     '微积分、级数、多元函数微分学。'),
('大学英语(III)',    6, 3, 60,  '公共必修', '周二 08:00-09:40 / 周四 10:00-11:40', '外语楼-305',  '学术英语写作与口语表达。'),
('数据结构',         7, 4, 5,   '专业必修', '周二 14:00-15:40 / 周四 14:00-15:40', '信息楼-401',  '数据结构与算法。容量设小便于演示审批。'),
('操作系统',         7, 4, 80,  '专业必修', '周二 16:00-17:40 / 周四 08:00-09:40', '信息楼-501',  '进程、内存、文件系统。'),
('人工智能导论',     8, 3, 4,   '专业选修', '周五 14:00-15:40',                    '信息楼-601',  'AI基础概念。容量设小便于演示审批。'),
('Web前端开发',      9, 3, 50,  '专业选修', '周二 10:00-11:40',                    '信息楼-302',  'HTML/CSS/JS及现代框架。'),
('数据库系统',       9, 3, 60,  '专业选修', '周四 16:00-17:40',                    '信息楼-305',  '关系模型、SQL、事务。'),
('计算机网络',       8, 3, 70,  '专业选修', '周一 16:00-17:40',                    '信息楼-405',  'TCP/IP、路由、交换。');

-- 预置选课报名（演示审批场景）
-- student_id: 2023010102=2, 2023010103=3, 2023010104=4, 2023010105=5
-- course_id:  数据结构=3, 人工智能导论=5
INSERT INTO enrollments (student_id, course_id, round, points, status) VALUES
-- 数据结构 (容量5，4人报名，全部待审批，老师需审批)
(2, 3, 1, 40, 'pending'),
(3, 3, 1, 55, 'pending'),
(4, 3, 1, 30, 'pending'),
(5, 3, 1, 50, 'pending'),
-- 人工智能导论 (容量4，4人报名，全部待审批)
(2, 5, 1, 45, 'pending'),
(3, 5, 1, 60, 'pending'),
(4, 5, 1, 35, 'pending'),
(5, 5, 1, 50, 'pending');

-- ============================================================
-- 验证
-- ============================================================
SELECT '用户数' AS 统计项, COUNT(*) AS 数量 FROM users
UNION ALL
SELECT '课程数', COUNT(*) FROM courses
UNION ALL
SELECT '报名数', COUNT(*) FROM enrollments
UNION ALL
SELECT '系统配置', COUNT(*) FROM system_config;
