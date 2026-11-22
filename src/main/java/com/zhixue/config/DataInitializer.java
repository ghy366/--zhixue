package com.zhixue.config;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private UserRepository userRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private SemesterRepository semesterRepo;
    @Autowired private ClassroomRepository classroomRepo;
    @Autowired private AnnouncementRepository annRepo;
    @Autowired private GradeRequirementRepository gradeReqRepo;
    @Autowired private GradeRepository gradeRepo;
    @Autowired private GradeRecordRepository recordRepo;
    @Autowired private MakeupExamRepository makeupRepo;

    @Override
    public void run(String... args) {
        if (sysRepo.count() == 0) {
            SystemConfig c = new SystemConfig();
            c.setCurrentRound(1);
            c.setRoundOpen(true);
            c.setMaxCreditPerSemester(24);
            try {
                sysRepo.save(c);
            } catch (DataIntegrityViolationException ignored) {
                // 定时任务可能在同一时刻创建了 id=1 的配置行；唯一键冲突说明配置已存在，继续初始化即可
            }
        }

        // 毕业要求：对齐厦门华厦学院——必修 / 公共选修 / 专业选修 三类，通识/实践/课程数不再单列。
        // 公共选修课按《本科学生网上选课指南》须修满 8 学分，每学期大一大二≤2、大三大四≤4。
        if (gradeReqRepo.count() == 0) {
            gradeReqRepo.saveAll(List.of(
                gradeReq(2021, null, "普通本科", 18, 8, 8),
                gradeReq(2023, null, "普通本科", 18, 8, 8),
                gradeReq(2023, "数据科学与大数据技术", "数据科学方向", 18, 8, 8),
                gradeReq(2024, null, "普通本科", 18, 8, 8),
                gradeReq(2025, null, "专升本", 16, 8, 8)
            ));
        }

        // 按种子账号判断，避免其他 Runner 先创建导师账号导致 count>0 而跳过学生/教师初始化
        if (userRepo.findByNo("admin").isEmpty()) {   // 以 admin 账号判定：任何已有用户体系的库不重复播种，避免 uk_no 冲突
            userRepo.saveAll(List.of(
                user("2023010101", "123456", "林晓宇", "student", "信息与智能机电学院", "数据科学与大数据技术", 2023, "数据科学2301班", "13800000001"),
                user("2023010102", "123456", "苏雨桐", "student", "信息与智能机电学院", "通信工程", 2023, "通信2301班", "13800000002"),
                user("2023010103", "123456", "陈昊然", "student", "信息与智能机电学院", "机器人工程", 2023, "机器人2301班", "13800000003"),
                user("2023010104", "123456", "王思琪", "student", "商务与管理学院", "会计学", 2023, "会计2301班", "13800000004"),
                user("2023010105", "123456", "李梓涵", "student", "人文学院", "网络与新媒体", 2023, "网媒2301班", "13800000005"),
                user("T001", "123456", "王建国", "teacher", "信息与智能机电学院", null, null, null, null),
                user("T002", "123456", "赵明", "teacher", "信息与智能机电学院", null, null, null, null),
                user("T003", "123456", "郑昊", "teacher", "信息与智能机电学院", null, null, null, null),
                user("T004", "123456", "黄丽", "teacher", "商务与管理学院", null, null, null, null),
                user("admin", "admin", "管理员", "admin", "教务处", null, null, null, null)
            ));
        }

        if (courseRepo.count() == 0) {
            // 课程表对齐厦门华厦学院：
            // - 必修课按计算机类培养方案排入（公共必修 + 专业必修），由教务系统自动排课，学生不自选；
            // - 选修课即学生真正「选课」的对象 = 全校公共选修课（通识选修），课程名取自华厦通识教育中心
            //   公开通知（2026-2027 一学期选课通知附件 + 网上选课指南），课程名/代码/教师取自华厦通识教育中心公开《通识选修课课程一览表》，1~2 学分、16~32 课时；
            // - 不设「通识选修」「实践课程」类别，二者统一归入公共选修 / 选修组。
            User t1 = userRepo.findByNo("T001").orElse(null);
            User t2 = userRepo.findByNo("T002").orElse(null);
            User t3 = userRepo.findByNo("T003").orElse(null);
            User t4 = userRepo.findByNo("T004").orElse(null);
            User t6 = userRepo.findByNo("T006").orElse(null);      // 人文学院
            User tGE1 = userRepo.findByNo("T005").orElse(null);    // 通识教育中心
            User tGE2 = userRepo.findByNo("T270").orElse(null);    // 通识教育中心
            User tBiz = userRepo.findByNo("T240").orElse(null);    // 商务与管理学院
            User tBiz2 = userRepo.findByNo("T007").orElse(null);   // 商务与管理学院
            User tEnv = userRepo.findByNo("T291").orElse(null);    // 环境与公共健康学院
            User tEnv2 = userRepo.findByNo("T292").orElse(null);
            User tEnv3 = userRepo.findByNo("T293").orElse(null);
            User tHum2 = userRepo.findByNo("T230").orElse(null);   // 人文学院
            courseRepo.saveAll(List.of(
                /* ===== 公共必修：全校共享，通识教育中心/信智教师授课 ===== */
                course("CS101", "计算机导论", t3, 2, 32, 120, "公共必修", "周一 08:00-09:40 / 周三 08:00-09:40", "求实楼-201", "计算机基础知识。", 1),
                course("MA101", "高等数学(上)", tGE1, 3, 48, 120, "公共必修", "周一 10:00-11:40 / 周四 10:00-11:40", "求实楼-201", "微积分基础。", 1),
                course("EN101", "大学英语(I)", tGE1, 2, 32, 120, "公共必修", "周二 08:00-09:40 / 周四 08:00-09:40", "闻道楼-201", "基础英语。", 1),
                course("MA102", "高等数学(下)", tGE2, 3, 48, 120, "公共必修", "周一 14:00-15:40 / 周三 14:00-15:40", "求实楼-201", "多元微积分。", 2),
                course("EN203", "大学英语(III)", tGE1, 2, 32, 120, "公共必修", "周二 10:00-11:40 / 周五 10:00-11:40", "闻道楼-201", "学术英语。", 3),
                /* ===== 专业必修：按学院区分，本学院教师授课 ===== */
                collegeCourse("CS103", "数据结构与算法", t2, 3, 48, 120, "周二 14:00-15:40 / 周四 14:00-15:40", "明志楼-201", "数据结构与算法。", 3, "信息与智能机电学院"),
                collegeCourse("CS201", "操作系统", t2, 3, 48, 120, "周三 16:00-17:40 / 周五 16:00-17:40", "明志楼-201", "进程、内存、文件系统。", 4, "信息与智能机电学院"),
                collegeCourse("CS402", "数据库系统", t4, 2, 32, 120, "周五 14:00-15:40", "明志楼-201", "关系模型、SQL。", 4, "信息与智能机电学院"),
                collegeCourse("BA201", "会计学原理", tBiz, 3, 48, 120, "周二 14:00-15:40 / 周四 14:00-15:40", "明志楼-202", "会计确认、计量与报表。", 3, "商务与管理学院"),
                collegeCourse("BA202", "供应链管理", tBiz2, 3, 48, 120, "周五 08:00-09:40 / 周六 08:00-09:40", "致远楼-202", "供应链计划、采购与物流。", 4, "商务与管理学院"),
                collegeCourse("BA203", "财务管理", tBiz, 2, 32, 120, "周五 14:00-15:40", "致远楼-202", "资金筹集与投资决策。", 5, "商务与管理学院"),
                collegeCourse("EH201", "环境科学概论", tEnv, 3, 48, 120, "周三 10:00-11:40 / 周六 10:00-11:40", "求实楼-201", "环境系统与污染控制。", 3, "环境与公共健康学院"),
                collegeCourse("EH202", "食品化学", tEnv2, 3, 48, 120, "周二 14:00-15:40 / 周四 14:00-15:40", "求实楼-203", "食品成分与化学变化。", 4, "环境与公共健康学院"),
                collegeCourse("EH203", "制药工程基础", tEnv3, 2, 32, 120, "周二 16:00-17:40", "求实楼-201", "药物合成与制剂工艺。", 5, "环境与公共健康学院"),
                collegeCourse("HU201", "视觉设计基础", t6, 3, 48, 120, "周一 16:00-17:40 / 周三 16:00-17:40", "齐礼楼-201", "平面构成、色彩与版式。", 3, "人文学院"),
                collegeCourse("HU202", "新媒体概论", tHum2, 2, 32, 120, "周二 16:00-17:40", "齐礼楼-202", "新媒体形态与内容生产。", 4, "人文学院"),
                collegeCourse("HU203", "商务英语精读", t6, 2, 32, 120, "周四 16:00-17:40", "闻道楼-201", "商务场景英语阅读。", 5, "人文学院"),
                /* ===== 选修：专业选修（先修链演示） ===== */
                collegeElec("CS301", "人工智能导论", t3, 2, 32, 60, "周六 14:00-15:40", "明志楼-201", "专业选修：AI基础概念。", "信息与智能机电学院"),
                collegeElec("CS302", "机器学习", t3, 2, 32, 60, "周日 14:00-15:40", "明志楼-201", "专业选修：机器学习基础（先修人工智能导论）。", "信息与智能机电学院"),
                collegeElec("CS401", "Web前端开发", t4, 2, 32, 60, "周六 16:00-17:40", "明志楼-201", "专业选修：HTML/CSS/JS。", "信息与智能机电学院"),
                collegeElec("CS501", "嵌入式系统", t2, 2, 32, 60, "周四 16:00-17:40", "明志楼-202", "专业选修：单片机与嵌入式开发。", "信息与智能机电学院"),
                /* ===== 专业选修：商务与管理学院（教务按培养方案自动安排） ===== */
                collegeElec("BA301", "市场营销学", tBiz, 2, 32, 60, "周一 16:00-17:40", "致远楼-202", "专业选修：市场细分与营销策略。", "商务与管理学院"),
                collegeElec("BA302", "电子商务运营", tBiz2, 2, 32, 60, "周二 16:00-17:40", "致远楼-203", "专业选修：电商平台运营与数据分析。", "商务与管理学院"),
                collegeElec("BA303", "人力资源管理", tBiz, 2, 32, 60, "周三 10:00-11:40", "致远楼-202", "专业选修：招聘、绩效与薪酬管理。", "商务与管理学院"),
                collegeElec("BA304", "财务报表分析", tBiz2, 2, 32, 60, "周日 10:00-11:40", "致远楼-203", "专业选修：三大报表解读与财务诊断。", "商务与管理学院"),
                /* ===== 专业选修：环境与公共健康学院 ===== */
                collegeElec("EH301", "食品营养学", tEnv, 2, 32, 60, "周一 19:00-20:30", "求实楼-203", "专业选修：营养素与膳食平衡。", "环境与公共健康学院"),
                collegeElec("EH302", "环境微生物学", tEnv2, 2, 32, 60, "周二 19:00-20:30", "求实楼-202", "专业选修：微生物在环境治理中的应用。", "环境与公共健康学院"),
                collegeElec("EH303", "药物分析", tEnv3, 2, 32, 60, "周三 19:00-20:30", "求实楼-203", "专业选修：药物质量控制与检测方法。", "环境与公共健康学院"),
                collegeElec("EH304", "健康评估", tEnv, 2, 32, 60, "周四 19:00-20:30", "求实楼-202", "专业选修：健康体检与风险评估。", "环境与公共健康学院"),
                /* ===== 专业选修：人文学院 ===== */
                collegeElec("HU301", "摄影艺术", t6, 2, 32, 60, "周五 19:00-20:30", "齐礼楼-203", "专业选修：摄影构图与后期。", "人文学院"),
                collegeElec("HU302", "网络传播实务", tHum2, 2, 32, 60, "周二 14:00-15:40", "齐礼楼-204", "专业选修：新媒体内容策划与传播。", "人文学院"),
                collegeElec("HU303", "广告创意设计", t6, 2, 32, 60, "周日 08:00-09:40", "齐礼楼-202", "专业选修：广告创意与视觉表现。", "人文学院"),
                collegeElec("HU304", "商务翻译", tHum2, 2, 32, 60, "周五 08:00-09:40", "闻道楼-203", "专业选修：商务场景英汉互译。", "人文学院"),
                /* ===== 选修：全校公共选修课（华厦真实课程，学生积分选课对象） ===== */
                course("40Hd052c", "中华花艺插花艺术", t1, 1, 16, 80, "公共选修", "周日 10:00-11:40", "齐礼楼-201", "交叉·美育｜黄芳 老师（华厦通识教育中心公开课程表）", null),
                course("40Hd050a", "思辨与写作", t2, 1, 16, 80, "公共选修", "周一 19:00-20:30", "齐礼楼-201", "交叉·数字素养｜刘海翔 老师（华厦通识教育中心公开课程表）", null),
                course("40Hd072d", "数字沟通与短视频制作", t3, 2, 32, 80, "公共选修", "周六 16:00-17:40", "致远楼-202", "交叉·数字素养｜周雯 老师（华厦通识教育中心公开课程表）", null),
                course("40Hd071d", "电影级幻灯片设计", t4, 1, 16, 80, "公共选修", "周日 14:00-15:40", "明志楼-202", "交叉·数字素养｜王阁 老师（华厦通识教育中心公开课程表）", null),
                course("40Hd053c", "三十六计与商战", t1, 2, 32, 80, "公共选修", "周日 16:00-17:40", "温故楼-201", "交叉｜窦锦伟 老师（华厦通识教育中心公开课程表）", null),
                course("40Hd068c", "看电影学金融", t2, 1, 16, 80, "公共选修", "周日 19:00-20:30", "齐礼楼-201", "交叉｜卢靖 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha149a", "自然文学经典导读", t3, 1, 16, 80, "公共选修", "周一 19:00-20:30", "齐礼楼-202", "人文·美育｜刘海翔 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha160a", "邂逅南音", t4, 2, 32, 80, "公共选修", "周三 19:00-20:30", "致远楼-201", "人文·美育｜罗纯祯 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha077a", "中国书画欣赏与练习", t1, 1, 16, 80, "公共选修", "周五 19:00-20:30", "明志楼-201", "人文·美育｜陈伟立 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha154b", "闽南语及闽南文化入门", t2, 2, 32, 80, "公共选修", "周六 14:00-15:40", "温故楼-202", "人文·美育｜曲晓妮 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha002d", "中国茶文化", t3, 1, 16, 80, "公共选修", "周六 19:00-20:30", "齐礼楼-201", "人文·美育｜周达 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha169a", "新媒体内容策划", t4, 1, 16, 80, "公共选修", "周四 10:00-11:40", "齐礼楼-201", "人文·数字素养｜陈连利 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha016c", "摄影艺术技能", t1, 1, 16, 80, "公共选修", "周三 19:00-20:30", "致远楼-202", "人文·美育｜周文磊 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha142a", "行走中国", t2, 1, 16, 80, "公共选修", "周五 19:00-20:30", "明志楼-202", "人文｜尹芳 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha162a", "综艺节目概览", t3, 1, 16, 80, "公共选修", "周六 14:00-15:40", "温故楼-203", "人文·美育｜蔡琳懿 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha024b", "唐诗宋词赏析", t4, 1, 16, 80, "公共选修", "周五 10:00-11:40", "齐礼楼-201", "人文·美育｜吴巍澎 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha163a", "园林花卉文化与鉴赏", t1, 1, 16, 80, "公共选修", "周一 14:00-15:40", "齐礼楼-202", "人文·美育｜占长青 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha007a", "定格动画一刻通", t2, 1, 16, 80, "公共选修", "周五 16:00-17:40", "致远楼-201", "人文·美育｜何婷 老师（华厦通识教育中心公开课程表）", null),
                course("40Ha098c", "网络营销文案", t3, 1, 16, 80, "公共选修", "周二 19:00-20:30", "明志楼-201", "人文｜胡星 老师（华厦通识教育中心公开课程表）", null),
                course("40Hb088c", "生活中的经济学", t4, 2, 32, 80, "公共选修", "周四 19:00-20:30", "温故楼-201", "社会｜卢靖 老师（华厦通识教育中心公开课程表）", null),
                course("40Hb093a", "沟通与演讲", t1, 2, 32, 80, "公共选修", "周六 08:00-09:40", "齐礼楼-201", "社会｜纪政雪子 老师（华厦通识教育中心公开课程表）", null),
                course("40Hb071a", "民法与生活", t2, 2, 32, 80, "公共选修", "周日 08:00-09:40", "齐礼楼-201", "社会｜陈鼎庄 老师（华厦通识教育中心公开课程表）", null),
                course("40Hb080d", "心理学与生活", t3, 1, 16, 80, "公共选修", "周六 10:00-11:40", "致远楼-201", "社会｜黄延梅 老师（华厦通识教育中心公开课程表）", null),
                course("40Hb096b", "行走俄罗斯", t4, 1, 16, 80, "公共选修", "周日 10:00-11:40", "明志楼-202", "社会｜王东星 老师（华厦通识教育中心公开课程表）", null),
                course("40Hb075a", "人文地理与旅游规划", t1, 1, 16, 80, "公共选修", "周日 14:00-15:40", "温故楼-203", "社会｜王亚凡 老师（华厦通识教育中心公开课程表）", null),
                course("40Hb079c", "职业压力管理", t2, 1, 16, 80, "公共选修", "周六 16:00-17:40", "齐礼楼-203", "社会｜陈剑鸿 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc079c", "编程之美", t3, 2, 32, 80, "公共选修", "周日 16:00-17:40", "齐礼楼-202", "自然·数字素养｜张原草 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc083c", "GPT实战应用：提升提示词技巧", t4, 1, 16, 80, "公共选修", "周六 19:00-20:30", "致远楼-202", "自然·数字素养｜黄智财 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc065c", "数据可视化之美", t1, 1, 16, 80, "公共选修", "周日 19:00-20:30", "明志楼-202", "自然·数字素养｜陈婷婷 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc064c", "EXCEL数据分析通识应用", t2, 1, 16, 80, "公共选修", "周一 08:00-09:40", "温故楼-202", "自然·数字素养｜杨凯淇 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc088a", "中医养生保健", t3, 1, 16, 80, "公共选修", "周二 08:00-09:40", "齐礼楼-202", "自然｜苏思颖 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc063a", "地球历史及其生命的奥秘", t4, 1, 16, 80, "公共选修", "周三 08:00-09:40", "齐礼楼-201", "自然｜陈剑鸿 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc084c", "人工智能与信息社会", t1, 1, 16, 80, "公共选修", "周四 08:00-09:40", "致远楼-201", "自然·数字素养｜卢靖 老师（华厦通识教育中心公开课程表）", null),
                course("40Hc044d", "化学与生活", t2, 1, 16, 80, "公共选修", "周五 08:00-09:40", "明志楼-201", "自然｜林若川 老师（华厦通识教育中心公开课程表）", null)
            ));
            // 先修链：机器学习 ← 人工智能导论
            Course cAI = courseRepo.findByNameContaining("人工智能").stream().findFirst().orElse(null);
            Course cML = courseRepo.findByNameContaining("机器学习").stream().findFirst().orElse(null);
            if (cML != null && cAI != null) { cML.setPrereqId(cAI.getId()); courseRepo.save(cML); }

            // ===== 历史学期还原课：按各学院真实教师轮换，还原非毕业学期每学期 >=7 门课 =====
            // PE- 前缀由 CourseGroup.isHistorical 识别：只用于成绩档案展示，不自动排课、不计毕业学分。
            if (courseRepo.findByCourseCode("PE-ISE-01") == null) {
                // 全局无冲突排课：历史学期还原课按 (星期,节次) 分散，教室同格不重复。
                // 与 zhixue_schedule_fix.py 的计算结果一致，清库重建也不会出现「一格堆 6 门课」。
                String[] HIST_TIME = {
                    "周六 19:00-20:30,周三 08:00-09:40,周一 19:00-20:30,周二 19:00-20:30,周三 19:00-20:30,周四 19:00-20:30,周三 10:00-11:40,周五 19:00-20:30,周六 08:00-09:40,周一 10:00-11:40,周二 10:00-11:40,周日 08:00-09:40,周日 19:00-20:30,周六 10:00-11:40,周日 10:00-11:40,周四 10:00-11:40,周三 14:00-15:40,周五 10:00-11:40,周六 14:00-15:40,周日 14:00-15:40,周一 16:00-17:40,周五 14:00-15:40,周六 16:00-17:40,周三 16:00-17:40,周二 16:00-17:40,周五 08:00-09:40,周日 16:00-17:40,周二 08:00-09:40,周四 08:00-09:40,周一 08:00-09:40",  // ISE
                    "周一 10:00-11:40,周日 08:00-09:40,周三 14:00-15:40,周五 14:00-15:40,周一 16:00-17:40,周三 16:00-17:40,周四 16:00-17:40,周五 16:00-17:40,周一 19:00-20:30,周六 10:00-11:40,周二 19:00-20:30,周日 10:00-11:40,周三 19:00-20:30,周四 19:00-20:30,周六 14:00-15:40,周日 14:00-15:40,周五 19:00-20:30,周六 08:00-09:40,周六 19:00-20:30,周日 19:00-20:30,周一 08:00-09:40,周二 08:00-09:40,周三 08:00-09:40,周四 08:00-09:40,周一 14:00-15:40,周五 08:00-09:40,周二 10:00-11:40,周六 16:00-17:40,周三 10:00-11:40,周日 16:00-17:40",  // BIZ
                    "周日 16:00-17:40,周六 19:00-20:30,周日 08:00-09:40,周二 10:00-11:40,周五 08:00-09:40,周日 19:00-20:30,周四 16:00-17:40,周五 16:00-17:40,周二 08:00-09:40,周一 08:00-09:40,周六 10:00-11:40,周一 19:00-20:30,周六 14:00-15:40,周三 08:00-09:40,周二 19:00-20:30,周四 10:00-11:40,周四 08:00-09:40,周日 10:00-11:40,周一 14:00-15:40,周三 19:00-20:30,周三 14:00-15:40,周五 10:00-11:40,周日 14:00-15:40,周四 19:00-20:30,周六 16:00-17:40,周三 10:00-11:40,周一 10:00-11:40,周四 14:00-15:40,周五 19:00-20:30,周六 08:00-09:40",  // ENV
                    "周日 16:00-17:40,周一 10:00-11:40,周六 19:00-20:30,周二 10:00-11:40,周三 14:00-15:40,周五 10:00-11:40,周二 14:00-15:40,周四 14:00-15:40,周六 14:00-15:40,周五 14:00-15:40,周一 16:00-17:40,周二 16:00-17:40,周日 19:00-20:30,周一 08:00-09:40,周三 16:00-17:40,周二 08:00-09:40,周四 16:00-17:40,周一 14:00-15:40,周四 10:00-11:40,周三 08:00-09:40,周一 19:00-20:30,周五 16:00-17:40,周二 19:00-20:30,周四 08:00-09:40,周三 19:00-20:30,周四 19:00-20:30,周五 19:00-20:30,周六 08:00-09:40,周日 08:00-09:40,周六 10:00-11:40",  // HUM
                };
                String[] HIST_ROOM = {
                    "明志楼-203,明志楼-202,明志楼-204,明志楼-203,明志楼-204,明志楼-203,明志楼-203,明志楼-204,明志楼-202,明志楼-202,明志楼-202,明志楼-203,明志楼-203,明志楼-202,明志楼-204,明志楼-202,明志楼-201,明志楼-202,明志楼-204,明志楼-204,明志楼-203,明志楼-203,明志楼-204,明志楼-202,明志楼-204,明志楼-204,明志楼-203,明志楼-203,明志楼-202,明志楼-203",
                    "致远楼-203,致远楼-204,致远楼-202,致远楼-204,致远楼-204,致远楼-203,致远楼-203,致远楼-202,致远楼-205,致远楼-203,致远楼-204,致远楼-205,致远楼-205,致远楼-204,致远楼-205,致远楼-205,致远楼-205,致远楼-203,致远楼-204,致远楼-204,致远楼-204,致远楼-204,致远楼-203,致远楼-203,致远楼-203,致远楼-205,致远楼-203,致远楼-205,致远楼-204,致远楼-204",
                    "求实楼-205,求实楼-205,求实楼-205,求实楼-204,求实楼-206,求实楼-205,求实楼-204,求实楼-203,求实楼-205,求实楼-205,求实楼-204,求实楼-206,求实楼-206,求实楼-204,求实楼-205,求实楼-203,求实楼-204,求实楼-206,求实楼-204,求实楼-206,求实楼-203,求实楼-203,求实楼-206,求实楼-205,求实楼-206,求实楼-205,求实楼-204,求实楼-201,求实楼-206,求实楼-204",
                    "齐礼楼-206,齐礼楼-205,齐礼楼-206,齐礼楼-205,齐礼楼-204,齐礼楼-204,齐礼楼-205,齐礼楼-202,齐礼楼-207,齐礼楼-205,齐礼楼-205,齐礼楼-205,齐礼楼-206,齐礼楼-206,齐礼楼-204,齐礼楼-206,齐礼楼-205,齐礼楼-205,齐礼楼-204,齐礼楼-205,齐礼楼-207,齐礼楼-204,齐礼楼-206,齐礼楼-205,齐礼楼-207,齐礼楼-206,齐礼楼-207,齐礼楼-205,齐礼楼-206,齐礼楼-205",
                };
                int ci = 0;
                java.util.LinkedHashMap<String, java.util.List<String>> histPool = new java.util.LinkedHashMap<>();                histPool.put("信息与智能机电学院", java.util.List.of("嵌入式系统设计", "传感器网络", "PLC原理与应用", "机器视觉基础", "工业控制网络", "信号与系统", "数字信号处理", "移动通信技术", "计算机网络工程", "数据库应用开发", "Python数据分析", "大数据可视化", "智能制造导论", "数控技术", "机电一体化", "液压与气动技术", "自动控制原理", "机器人运动学", "单片机原理", "EDA技术", "软件测试基础", "云计算基础", "信息安全导论", "Linux系统管理", "微信小程序开发", "移动应用开发", "物联网平台实训", "通信协议分析", "光纤通信技术", "智能硬件设计"));                histPool.put("商务与管理学院", java.util.List.of("微观经济学", "宏观经济学", "管理统计学", "运筹学基础", "消费者行为学", "品牌管理", "国际结算", "跨境电商实务", "仓储与配送管理", "采购与供应管理", "商务谈判", "创业基础", "公司战略管理", "市场营销调研", "网络营销实务", "客户关系管理", "税法实务", "审计学基础", "成本会计", "管理会计", "金融学导论", "投资学基础", "保险学", "证券投资分析", "会展策划", "物流信息系统", "供应链金融", "质量管理", "项目管理", "组织行为学"));                histPool.put("环境与公共健康学院", java.util.List.of("环境监测技术", "水污染控制工程", "大气污染控制", "固体废物处理", "环境生态学", "环境法规", "环境影响评价", "清洁生产", "食品安全检测", "食品工艺学", "食品微生物", "营养与膳食", "药理学基础", "药物化学", "药剂学", "药品质量管理", "康复评定学", "运动治疗学", "物理因子治疗", "中医康复学", "健康教育学", "流行病学", "公共卫生导论", "职业卫生", "实验室安全", "仪器分析", "色谱分析", "生物化学", "细胞生物学", "分子生物学导论"));                histPool.put("人文学院", java.util.List.of("传播学概论", "新闻采访与写作", "影视语言", "纪录片创作", "动画设计基础", "三维建模", "UI界面设计", "包装设计", "书籍装帧", "展示设计", "环境艺术赏析", "中外美术史", "设计美学", "艺术概论", "音乐基础", "合唱与指挥", "声乐基础", "钢琴即兴伴奏", "英语口译", "英语报刊阅读", "跨文化交际", "商务英语函电", "应用文写作", "现代汉语", "中国古代文学", "文案策划", "社交媒体运营", "短视频创作", "直播运营实务", "数字出版"));                java.util.LinkedHashMap<String, String> histAbbr = new java.util.LinkedHashMap<>();                histAbbr.put("信息与智能机电学院", "ISE");                histAbbr.put("商务与管理学院", "BIZ");                histAbbr.put("环境与公共健康学院", "ENV");                histAbbr.put("人文学院", "HUM");                java.util.LinkedHashMap<String, String> histLoc = new java.util.LinkedHashMap<>();                histLoc.put("信息与智能机电学院", "明志楼");                histLoc.put("商务与管理学院", "致远楼");                histLoc.put("环境与公共健康学院", "求实楼");                histLoc.put("人文学院", "齐礼楼");                java.util.List<Course> histCourses = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, java.util.List<String>> en : histPool.entrySet()) {
                    String college = en.getKey();
                    java.util.List<User> teachers = userRepo.findAll().stream()
                            .filter(u -> "teacher".equals(u.getRole()) && college.equals(u.getCollege()))
                            .sorted(java.util.Comparator.comparing(User::getNo))
                            .toList();
                    if (teachers.isEmpty()) { ci++; continue; }
                    java.util.List<String> names = en.getValue();
                    for (int i = 0; i < names.size(); i++) {
                        User teacher = teachers.get(i % teachers.size());
                        Course hc = course("PE-" + histAbbr.get(college) + "-" + String.format("%02d", i + 1),
                                names.get(i), teacher, 2, 32, 120, "专业选修",
                                HIST_TIME[ci].split(",")[i], HIST_ROOM[ci].split(",")[i],
                                "专业选修：" + names.get(i) + "（历史学期已修）", null);
                        hc.setTargetCollege(college);
                        histCourses.add(hc);
                    }
                    ci++;
                }
                courseRepo.saveAll(histCourses);
            }

            // 授课教师按「用户表中该学院的真实教师」轮换分配，避免同一教师包揽全部课程：
            // 公共必修/公共选修 → 通识教育中心；专业必修/专业选修 → 课程 targetCollege 所属学院。
            java.util.Map<String, java.util.List<User>> teachersByCollege = new java.util.LinkedHashMap<>();
            for (User t : userRepo.findAll()) {
                if (!"teacher".equals(t.getRole()) || t.getCollege() == null) continue;
                teachersByCollege.computeIfAbsent(t.getCollege(), k -> new java.util.ArrayList<>()).add(t);
            }
            for (java.util.List<User> list : teachersByCollege.values()) {
                list.sort(java.util.Comparator.comparing(User::getNo));
            }
            java.util.List<User> geTeachers = teachersByCollege.getOrDefault("通识教育中心", java.util.List.of());
            java.util.Map<String, Integer> assignIdx = new java.util.HashMap<>();
            java.util.List<Course> reassign = new java.util.ArrayList<>();
            for (Course c : courseRepo.findAll()) {
                boolean common = "公共必修".equals(c.getCategory()) || "公共选修".equals(c.getCategory());
                String college = common ? "通识教育中心" : c.getTargetCollege();
                java.util.List<User> pool2 = college == null ? java.util.List.of()
                        : teachersByCollege.getOrDefault(college, java.util.List.of());
                if (pool2.isEmpty()) pool2 = geTeachers;
                if (pool2.isEmpty()) continue;
                int idx = assignIdx.getOrDefault(college, 0);
                c.setTeacherId(pool2.get(idx % pool2.size()).getId());
                assignIdx.put(college, idx + 1);
                reassign.add(c);
            }
            if (!reassign.isEmpty()) courseRepo.saveAll(reassign);
        }

        if (enrollmentRepo.count() == 0) {
            Course ds = courseRepo.findByNameContaining("数据结构").stream().findFirst().orElse(null);
            Course ai = courseRepo.findByNameContaining("人工智能").stream().findFirst().orElse(null);
            if (ds != null) {
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010102").get().getId(), ds.getId(), 1, 40, "approved"));
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010103").get().getId(), ds.getId(), 1, 55, "approved"));
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010104").get().getId(), ds.getId(), 1, 30, "pending"));
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010105").get().getId(), ds.getId(), 1, 50, "pending"));
            }
            if (ai != null) {
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010102").get().getId(), ai.getId(), 1, 45, "pending"));
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010103").get().getId(), ai.getId(), 1, 60, "pending"));
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010104").get().getId(), ai.getId(), 1, 35, "pending"));
                enrollmentRepo.save(enroll(userRepo.findByNo("2023010105").get().getId(), ai.getId(), 1, 50, "pending"));
            }
        }

        // 演示重修补考链路：林晓宇数据结构挂科（总评 41.2），可在成绩页看到「待补考」、
        // 毕业页看到必修缺失 + 重修中，教师端可录补考成绩
        if (gradeRepo.count() == 0) {
            User failStu = userRepo.findByNo("2023010101").orElse(null);
            Course failCourse = courseRepo.findByNameContaining("数据结构").stream().findFirst().orElse(null);
            if (failStu != null && failCourse != null) {
                Grade g = new Grade();
                g.setStudentId(failStu.getId());
                g.setCourseId(failCourse.getId());
                g.setRegularScore(40.0);
                g.setFinalScore(42.0);
                g.setTotalScore(41.2);   // 40×40% + 42×60%
                g.setExamType("normal");
                g.setAttempt(1);
                gradeRepo.save(g);

                // 同步写入成绩流水，保证 GPA / 毕业进度 / 补考生成与最新成绩同源
                GradeRecord rec = new GradeRecord();
                rec.setStudentId(failStu.getId());
                rec.setCourseId(failCourse.getId());
                rec.setAttemptNo(1);
                rec.setExamType("normal");
                rec.setRegularScore(40.0);
                rec.setFinalScore(42.0);
                rec.setTotalScore(41.2);
                rec.setSemesterNo(failCourse.getSemesterNo());
                recordRepo.save(rec);

                // 同步生成一条待报名补考，学生端「我的补考」可立即演示
                MakeupExam mk = new MakeupExam();
                mk.setStudentId(failStu.getId());
                mk.setCourseId(failCourse.getId());
                mk.setGradeRecordId(rec.getId());
                mk.setSemesterNo(failCourse.getSemesterNo());
                mk.setAttemptNo(1);
                mk.setStatus("ELIGIBLE");
                makeupRepo.save(mk);
            }
        }

        if (semesterRepo.count() == 0) {
            Semester s1 = new Semester();
            s1.setName("2024-2025学年第一学期");
            s1.setStartDate(LocalDate.of(2024, 9, 1));
            s1.setSelectionStart(LocalDate.of(2024, 9, 1));
            s1.setSelectionEnd(LocalDate.of(2025, 1, 15));
            s1.setStatus("已结束");
            s1.setIsCurrent(false);
            semesterRepo.save(s1);

            Semester s2 = new Semester();
            s2.setName("2024-2025学年第二学期");
            s2.setStartDate(LocalDate.of(2025, 2, 17));
            s2.setSelectionStart(LocalDate.of(2025, 2, 1));
            s2.setSelectionEnd(LocalDate.of(2025, 7, 1));
            s2.setStatus("进行中");
            s2.setIsCurrent(true);
            semesterRepo.save(s2);

            Semester s3 = new Semester();
            s3.setName("2025-2026学年第一学期");
            s3.setStartDate(LocalDate.of(2025, 9, 1));
            s3.setSelectionStart(LocalDate.of(2025, 9, 1));
            s3.setSelectionEnd(LocalDate.of(2026, 1, 15));
            s3.setStatus("未开始");
            s3.setIsCurrent(false);
            semesterRepo.save(s3);
        }

        if (classroomRepo.count() == 0) {
            // 教室种子：楼名 / 层数严格对齐学生端 3D 沙盘 CAMPUS_BUILDINGS，房号 = 楼层×100+序号
            classroomRepo.saveAll(List.of(
                classroom("明志楼-101", "明志楼-101", 160, "明志楼", "明志楼", "阶梯教室", 1, "多媒体、投影仪、扩音器、录播、空调", "正常"),
                classroom("明志楼-102", "明志楼-102", 120, "明志楼", "明志楼", "多媒体教室", 1, "多媒体、投影仪、扩音器、空调", "正常"),
                classroom("明志楼-301", "明志楼-301", 60, "明志楼", "明志楼", "计算机实验室", 3, "电脑、投影仪、多媒体、空调", "正常"),
                classroom("求实楼-101", "求实楼-101", 200, "求实楼", "求实楼", "阶梯教室", 1, "多媒体、投影仪、扩音器、录播、空调", "正常"),
                classroom("闻道楼-102", "闻道楼-102", 40, "闻道楼", "闻道楼", "语音室", 1, "语音设备、投影仪、空调", "正常"),
                classroom("大象楼-201", "大象楼-201", 60, "大象楼", "大象楼", "普通教室", 2, "白板、空调", "正常"),
                classroom("大器楼-101", "大器楼-101", 160, "大器楼", "大器楼", "阶梯教室", 1, "多媒体、投影仪、扩音器、录播、空调", "正常"),
                classroom("培训中心一期-101", "培训中心一期-101", 40, "培训中心一期", "培训中心一期", "实训室", 1, "实训设备、白板、空调", "维护中")
            ));
        }

        if (annRepo.count() == 0) {
            User admin = userRepo.findByNo("admin").orElse(null);
            announcement(admin, "第一轮选课将于 9 月 8 日 9:00 开始",
                "本轮选课时间为 09-08 09:00 至 09-15 17:00。每人每轮 50 积分，可自由分配到想选的公共选修课；"
                + "建议先在「课程浏览」把想上的课加入「我的意向」，开放后一键提交。",
                "选课通知", true);
            announcement(admin, "关于候补递补规则的说明",
                "课程满员后可加入候补队列；有人退课时，系统按排队顺序自动递补，并通知你在限时内确认，"
                + "超时名额将释放给下一位。候补确认时限可在管理员端配置。",
                "系统公告", false);
        }
    }

    private Announcement announcement(User publisher, String title, String content, String type, boolean pinned) {
        Announcement a = new Announcement();
        a.setTitle(title);
        a.setContent(content);
        a.setType(type);
        a.setPinned(pinned);
        a.setPublisherId(publisher != null ? publisher.getId() : null);
        a.setPublisherName(publisher != null ? publisher.getName() : "教务处");
        a.setCreatedAt(LocalDateTime.now());
        return annRepo.save(a);
    }

    private User user(String no, String pwd, String name, String role, String college, String major, Integer grade, String classNo, String phone) {
        User u = new User();
        u.setNo(no); u.setPassword(new com.zhixue.util.PasswordUtil().encode(pwd)); u.setName(name); u.setRole(role);
        u.setCollege(college); u.setMajor(major); u.setGrade(grade);
        u.setClassNo(classNo); u.setPhone(phone);
        if ("student".equals(role)) u.setAvatar("🎓");
        else if ("teacher".equals(role)) u.setAvatar("👨‍🏫");
        else if ("admin".equals(role)) u.setAvatar("👑");
        return u;
    }

    private Course course(String code, String name, User teacher, int credit, int hours, int cap, String cat, String time, String loc, String intro, Integer semesterNo) {
        Course c = new Course();
        c.setCourseCode(code); c.setName(name); c.setTeacherId(teacher != null ? teacher.getId() : null);
        c.setCredit(credit); c.setHours(hours); c.setCapacity(cap); c.setCategory(cat);
        c.setTime(time); c.setLocation(loc); c.setIntro(intro);
        c.setSemesterNo(semesterNo);   // 培养方案开课学期（选修为 null）
        return c;
    }

    /** 专业必修课：带适用学院，自动排课时只排给该学院学生 */
    private Course collegeCourse(String code, String name, User teacher, int credit, int hours, int cap,
                                 String time, String loc, String intro, Integer semesterNo, String college) {
        Course c = course(code, name, teacher, credit, hours, cap, "专业必修", time, loc, intro, semesterNo);
        c.setTargetCollege(college);
        return c;
    }

    /** 专业选修课：带适用学院，教务按培养方案自动排入课表（学生不自选） */
    private Course collegeElec(String code, String name, User teacher, int credit, int hours, int cap,
                               String time, String loc, String intro, String college) {
        Course c = course(code, name, teacher, credit, hours, cap, "专业选修", time, loc, intro, null);
        c.setTargetCollege(college);
        return c;
    }

    private Enrollment enroll(Long stuId, Long courseId, int round, int points, String status) {
        Enrollment e = new Enrollment();
        e.setStudentId(stuId); e.setCourseId(courseId); e.setRound(round);
        e.setPoints(points); e.setStatus(status);
        return e;
    }

    private Classroom classroom(String no, String name, int cap, String loc, String building,
                                String type, int floorNo, String equip, String status) {
        Classroom c = new Classroom();
        c.setRoomNo(no); c.setName(name); c.setCapacity(cap); c.setLocation(loc);
        c.setBuilding(building); c.setType(type); c.setFloorNo(floorNo);
        c.setEquipment(equip); c.setStatus(status);
        return c;
    }

    private GradeRequirement gradeReq(int grade, String major, String label, int required, int elective, int publicElec) {
        GradeRequirement g = new GradeRequirement();
        g.setGrade(grade); g.setMajor(major); g.setLabel(label);
        g.setGradReqRequired(required); g.setGradReqElective(elective);
        g.setGradReqGeneral(publicElec);   // 公共选修（华厦硬指标 8 学分）
        return g;
    }
}
