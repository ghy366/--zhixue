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

    @Override
    public void run(String... args) {
        if (sysRepo.count() == 0) {
            SystemConfig c = new SystemConfig();
            c.setCurrentRound(1);
            c.setRoundOpen(true);
            c.setMaxCreditPerSemester(4);
            c.setGradReqCourses(9);
            try {
                sysRepo.save(c);
            } catch (DataIntegrityViolationException ignored) {
                // 定时任务可能在同一时刻创建了 id=1 的配置行；唯一键冲突说明配置已存在，继续初始化即可
            }
        }

        // 按年级差异化毕业要求（参考真实高校：专升本学制短，毕业要求低于普通本科）
        if (gradeReqRepo.count() == 0) {
            gradeReqRepo.saveAll(List.of(
                gradeReq(2021, null, "普通本科", 6, 5, 3, 2, 9),
                gradeReq(2023, null, "普通本科", 6, 5, 3, 2, 9),
                gradeReq(2023, "软件工程", "软件工程方向", 6, 6, 4, 2, 10),
                gradeReq(2024, null, "普通本科", 6, 5, 3, 2, 9),
                gradeReq(2025, null, "专升本", 4, 3, 2, 1, 6)
            ));
        }

        if (userRepo.count() == 0) {
            userRepo.saveAll(List.of(
                user("2023010101", "123456", "林晓宇", "student", "计算机学院", "软件工程", 2023, "软工2301班", "13800000001"),
                user("2023010102", "123456", "苏雨桐", "student", "计算机学院", "计算机科学与技术", 2023, "计科2301班", "13800000002"),
                user("2023010103", "123456", "陈昊然", "student", "计算机学院", "软件工程", 2023, "软工2301班", "13800000003"),
                user("2023010104", "123456", "王思琪", "student", "信息学院", "信息安全", 2023, "信安2301班", "13800000004"),
                user("2023010105", "123456", "李梓涵", "student", "计算机学院", "软件工程", 2023, "软工2302班", "13800000005"),
                user("T001", "123456", "王建国", "teacher", "数学学院", null, null, null, null),
                user("T002", "123456", "赵明", "teacher", "计算机学院", null, null, null, null),
                user("T003", "123456", "郑昊", "teacher", "计算机学院", null, null, null, null),
                user("T004", "123456", "黄丽", "teacher", "计算机学院", null, null, null, null),
                user("admin", "admin", "管理员", "admin", "教务处", null, null, null, null)
            ));
        }

        if (courseRepo.count() == 0) {
            // 先修链演示：机器学习需先通过人工智能导论；数据库系统需先通过数据结构（必修挂科重修通过后放行）
            User t1 = userRepo.findByNo("T001").orElse(null);
            User t2 = userRepo.findByNo("T002").orElse(null);
            User t3 = userRepo.findByNo("T003").orElse(null);
            User t4 = userRepo.findByNo("T004").orElse(null);
            courseRepo.saveAll(List.of(
                course("CS101", "计算机导论", t1, 2, 48, 120, "公共必修", "周一 08:00-09:40 / 周三 10:00-11:40", "求实楼-201", "计算机基础知识。", 1),
                course("CS103", "数据结构与算法", t2, 3, 64, 5, "专业必修", "周二 14:00-15:40 / 周四 14:00-15:40", "明志楼-401", "数据结构与算法。", 4),
                course("CS201", "操作系统", t2, 3, 64, 80, "专业必修", "周二 16:00-17:40 / 周四 08:00-09:40", "明志楼-302", "进程、内存、文件系统。", 5),
                course("CS301", "人工智能导论", t3, 2, 48, 4, "专业选修", "周五 14:00-15:40", "明志楼-303", "AI基础概念。", null),
                course("CS302", "机器学习", t3, 2, 48, 60, "专业选修", "周三 14:00-15:40", "明志楼-202", "机器学习基础。", null),
                course("CS401", "Web前端开发", t4, 2, 48, 50, "专业选修", "周二 10:00-11:40", "明志楼-301", "HTML/CSS/JS。", null),
                course("CS402", "数据库系统", t4, 2, 48, 60, "专业选修", "周四 16:00-17:40", "明志楼-203", "关系模型、SQL。", null),
                course("EN203", "大学英语(III)", t1, 2, 48, 60, "公共必修", "周二 08:00-09:40 / 周四 10:00-11:40", "闻道楼-103", "学术英语。", 3)
            ));
            // 先修链：机器学习 ← 人工智能导论；数据库系统 ← 数据结构（未通过先修不可选修）
            Course cAI = courseRepo.findByNameContaining("人工智能").stream().findFirst().orElse(null);
            Course cML = courseRepo.findByNameContaining("机器学习").stream().findFirst().orElse(null);
            Course cDS = courseRepo.findByNameContaining("数据结构").stream().findFirst().orElse(null);
            Course cDB = courseRepo.findByNameContaining("数据库").stream().findFirst().orElse(null);
            if (cML != null && cAI != null) { cML.setPrereqId(cAI.getId()); courseRepo.save(cML); }
            if (cDB != null && cDS != null) { cDB.setPrereqId(cDS.getId()); courseRepo.save(cDB); }
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
                "本轮选课时间为 09-08 09:00 至 09-15 17:00。每人每轮 60 积分，可自由分配到想选的课程；"
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

    private GradeRequirement gradeReq(int grade, String major, String label, int required, int elective, int general, int practice, int courses) {
        GradeRequirement g = new GradeRequirement();
        g.setGrade(grade); g.setMajor(major); g.setLabel(label);
        g.setGradReqRequired(required); g.setGradReqElective(elective);
        g.setGradReqGeneral(general); g.setGradReqPractice(practice);
        g.setGradReqCourses(courses);
        return g;
    }
}
