feat: 智学选课系统 v1.0 — 多轮积分制选课 + 毕设指导工作台

【项目简介】
智学选课管理系统，一个面向中小型高校的多轮积分制选课平台。
三角色（学生/教师/管理员）+ 完整毕设指导工作台模块。

【技术栈】
- 后端：Spring Boot 3.3.5 + JPA + MySQL 8 + Maven
- 前端：原生 HTML/CSS/JavaScript（单文件 app.js，零框架依赖）
- Java 17 · 内嵌 Tomcat · 单 jar 部署

【代码规模】
20 张数据库表 / 20 Controller / 19 Service / 20 Repository · 105 文件

【核心功能 — 多轮积分选课系统】
├─ 学生端：注册登录、浏览课程、积分竞价选课、候补队列自动递补
│          查看课表、成绩、毕业进度、个人中心
├─ 教师端：发布课程、智能排课、成绩管理、查看选课学生
└─ 管理员端：轮次管理、系统配置、数据导入导出、操作日志审计

【核心算法】
① 积分竞价排序（同课按 points DESC + 报名时间 ASC）
② 候补队列自动递补（退课触发 + @Scheduled 定时兜底）
③ 课表冲突检测（时间/教室双重校验）
④ 智能排课贪心算法

【毕设指导工作台 — 新增完整模块】
├─ 导师双向选择（浏览教师能力 → 申请 → 导师审批）
├─ 论文私密管理（versionNo 自动递增 + advisorId 级别文件隔离）
├─ 师生私聊（同一 /api/workspace/chat 接口，真实姓名显示，自动标记已读）
└─ 导师公告（纯文本或带附件，一键推送给所有自己的学生）

【关键设计 — advisorId 文件隔离】
所有论文/消息/公告都带 graduate_advisor 表的主键 advisorId
下载接口双重校验：ga.studentId == 当前用户ID 或 ga.teacherId == 当前用户ID
不匹配 → 403 拒绝，跨导师/跨学生数据完全不可见

【数据库 20 张表】
核心选课（9）：User / Course / Enrollment / Waitlist / Grade / Semester / Classroom / SystemConfig / OperationLog
毕设新增（11）：GraduateAdvisor / GraduatePaper / GraduateMessage / GraduateAnnouncement / GraduateRequirement / AdvisorCapacity / Announcement / AnnouncementRead / Notification / CourseIntent / CourseReview

【部署方式】
java -jar target/zhixue-selection-1.0.0.jar
默认 http://localhost:8080  （MySQL 需配置 application.yml）

【默认账号】
学生：2023010101 / 123456
教师：T001 / 123456
管理员：admin / admin
