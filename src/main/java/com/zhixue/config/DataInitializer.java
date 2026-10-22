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

    @Override
    public void run(String... args) {
        if (sysRepo.count() == 0) {
            SystemConfig c = new SystemConfig();
            c.setCurrentRound(1);
            c.setRoundOpen(true);
            try {
                sysRepo.save(c);
            } catch (DataIntegrityViolationException ignored) {
                // 定时任务可能在同一时刻创建了 id=1 的配置行；唯一键冲突说明配置已存在，继续初始化即可
            }
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
            User t1 = userRepo.findByNo("T001").orElse(null);
            User t2 = userRepo.findByNo("T002").orElse(null);
            User t3 = userRepo.findByNo("T003").orElse(null);
            User t4 = userRepo.findByNo("T004").orElse(null);
            courseRepo.saveAll(List.of(
                course("CS101", "计算机导论", t1, 3, 48, 120, "公共必修", "周一 08:00-09:40 / 周三 10:00-11:40", "教1-201", "计算机基础知识。"),
                course("CS103", "数据结构与算法", t2, 4, 64, 5, "专业必修", "周二 14:00-15:40 / 周四 14:00-15:40", "信息楼-401", "数据结构与算法。"),
                course("CS201", "操作系统", t2, 4, 64, 80, "专业必修", "周二 16:00-17:40 / 周四 08:00-09:40", "信息楼-501", "进程、内存、文件系统。"),
                course("CS301", "人工智能导论", t3, 3, 48, 4, "专业选修", "周五 14:00-15:40", "信息楼-601", "AI基础概念。"),
                course("CS302", "机器学习", t3, 3, 48, 60, "专业选修", "周三 14:00-15:40", "信息楼-602", "机器学习基础。"),
                course("CS401", "Web前端开发", t4, 3, 48, 50, "专业选修", "周二 10:00-11:40", "信息楼-302", "HTML/CSS/JS。"),
                course("CS402", "数据库系统", t4, 3, 48, 60, "专业选修", "周四 16:00-17:40", "信息楼-305", "关系模型、SQL。"),
                course("EN203", "大学英语(III)", t1, 3, 48, 60, "公共必修", "周二 08:00-09:40 / 周四 10:00-11:40", "外语楼-305", "学术英语。")
            ));
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
            classroomRepo.saveAll(List.of(
                classroom("A101", "教学楼A101", 120, "教学楼A座", "多媒体", "正常"),
                classroom("A102", "教学楼A102", 80, "教学楼A座", "多媒体", "正常"),
                classroom("A201", "教学楼A201", 60, "教学楼A座", "多媒体", "正常"),
                classroom("A202", "教学楼A202", 60, "教学楼A座", "多媒体", "维护中"),
                classroom("B101", "教学楼B101", 120, "教学楼B座", "多媒体、投影仪", "正常"),
                classroom("L301", "理学楼301", 60, "理学楼", "多媒体", "正常"),
                classroom("L302", "理学楼302", 40, "理学楼", "计算机实验室", "正常"),
                classroom("L303", "理学楼303", 40, "理学楼", "计算机实验室", "正常")
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
        u.setNo(no); u.setPassword(pwd); u.setName(name); u.setRole(role);
        u.setCollege(college); u.setMajor(major); u.setGrade(grade);
        u.setClassNo(classNo); u.setPhone(phone);
        if ("student".equals(role)) u.setAvatar("🎓");
        else if ("teacher".equals(role)) u.setAvatar("👨‍🏫");
        else if ("admin".equals(role)) u.setAvatar("👑");
        return u;
    }

    private Course course(String code, String name, User teacher, int credit, int hours, int cap, String cat, String time, String loc, String intro) {
        Course c = new Course();
        c.setCourseCode(code); c.setName(name); c.setTeacherId(teacher != null ? teacher.getId() : null);
        c.setCredit(credit); c.setHours(hours); c.setCapacity(cap); c.setCategory(cat);
        c.setTime(time); c.setLocation(loc); c.setIntro(intro);
        return c;
    }

    private Enrollment enroll(Long stuId, Long courseId, int round, int points, String status) {
        Enrollment e = new Enrollment();
        e.setStudentId(stuId); e.setCourseId(courseId); e.setRound(round);
        e.setPoints(points); e.setStatus(status);
        return e;
    }

    private Classroom classroom(String no, String name, int cap, String loc, String equip, String status) {
        Classroom c = new Classroom();
        c.setRoomNo(no); c.setName(name); c.setCapacity(cap); c.setLocation(loc);
        c.setEquipment(equip); c.setStatus(status);
        return c;
    }
}
