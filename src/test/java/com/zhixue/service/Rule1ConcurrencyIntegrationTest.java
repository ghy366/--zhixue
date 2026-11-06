package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【规则一验证·集成】并发选课不超上限 + 退课后学分/门数回退。
 * 使用内存 H2（MySQL 模式）真实事务与行锁，不连外部 MySQL。
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:ruletest;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
})
class Rule1ConcurrencyIntegrationTest {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private GraduationService graduationService;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository sysRepo;

    private Long sid;

    @BeforeEach
    void clean() {
        enrollmentRepo.deleteAll();
        courseRepo.deleteAll();
        userRepo.deleteAll();

        SystemConfig cfg = sysRepo.findById(1L).orElseGet(() -> sysRepo.save(new SystemConfig()));
        cfg.setCurrentRound(1);
        cfg.setRoundOpen(true);
        cfg.setAutoSchedule(false);          // 关闭自动巡检，状态完全由测试控制
        cfg.setMaxCreditPerSemester(24);
        cfg.setGradReqRequired(12);
        cfg.setGradReqElective(10);
        cfg.setGradReqGeneral(6);
        cfg.setGradReqPractice(4);
        sysRepo.save(cfg);

        User u = new User();
        u.setNo("S_TEST");
        u.setPassword("test123");
        u.setName("并发测试学生");
        u.setRole("student");
        u.setGrade(2023);
        sid = userRepo.save(u).getId();
    }

    /** 场景 1.7：同一学生并发提交 3 门选修，最终（结算后）最多中选 2 门选修（规则断言） */
    @Test
    void R1_concurrentThreeCourses_atMostTwoApproved_byRule() throws Exception {
        Long c1 = saveCourse("并发课一", "周一 08:00-09:40");
        Long c2 = saveCourse("并发课二", "周二 08:00-09:40");
        Long c3 = saveCourse("并发课三", "周三 08:00-09:40");
        List<Long> cids = List.of(c1, c2, c3);

        concurrentEnroll(cids);

        // 第 1、2 轮只是登记 pending，统一由结算裁决——这里执行结算得到最终中选结果
        enrollmentService.settleRound(1);

        long approved = countApproved();
        assertTrue(approved <= 2,
                "按规则【每学期最多 2 门选修】并发 3 门选修最多中选 2 门；实际中选 " + approved + " 门（门数上限未实现）");
    }

    /** 场景 1.8（对照）：学分上限下并发 3 门 2 分课，结算后最多中选 2 门（当前实现真实语义，验证并发安全） */
    @Test
    void R1_concurrent_creditLimit_noOvershoot_actualBehavior() throws Exception {
        SystemConfig cfg = sysRepo.findById(1L).orElseThrow();
        cfg.setMaxCreditPerSemester(4);      // 3 门 × 2 分 → 只容得下 2 门
        sysRepo.save(cfg);

        Long c1 = saveCourse("学分课一", "周一 08:00-09:40", 2);
        Long c2 = saveCourse("学分课二", "周二 08:00-09:40", 2);
        Long c3 = saveCourse("学分课三", "周三 08:00-09:40", 2);

        concurrentEnroll(List.of(c1, c2, c3));
        enrollmentService.settleRound(1);

        long approved = countApproved();
        assertTrue(approved <= 2, "学分上限 4 分并发提交，最终中选不得超过 2 门；实际 " + approved);
    }

    /** 场景 1.9：退课后，学分与已选门数应正确回退 */
    @Test
    void R1_drop_rollsBackCreditsAndCount() {
        // 第 3 轮先到先得：报名即录取，便于直接验证回退
        SystemConfig cfg = sysRepo.findById(1L).orElseThrow();
        cfg.setCurrentRound(3);
        sysRepo.save(cfg);
        Long cid = saveCourse("回退课", "周四 10:00-11:40", 2);

        Map<String, Object> r = enrollmentService.enroll(sid, cid, 0);
        assertEquals(true, r.get("success"), "第 3 轮报名应成功：" + r.get("msg"));
        assertEquals(1, countApproved());
        assertEquals(2, totalCredits());

        Map<String, Object> drop = enrollmentService.drop(sid, cid);
        assertEquals(true, drop.get("success"), "退课应成功：" + drop.get("msg"));
        assertEquals(0, countApproved(), "退课后已选门数应回退为 0");
        assertEquals(0, totalCredits(), "退课后学分应回退为 0");
    }

    /**
     * 场景 1.10：必修课不占选修门数额度。
     * 已有 8 门必修的学生：仍可报 2 门选修，第 3 门选修被拒绝；
     * 新增 1 门必修后选修额度不变；退掉 1 门选修后额度回退可再报。
     */
    @Test
    void R1_requiredCoursesDoNotConsumeElectiveQuota() {
        SystemConfig cfg = sysRepo.findById(1L).orElseThrow();
        cfg.setCurrentRound(3);   // 先到先得：报名即录取，便于直接断言
        sysRepo.save(cfg);

        // 8 门必修直接落库为已录取（模拟教学计划自动排课，周五上课避开选修时段）
        for (int i = 1; i <= 8; i++) {
            Long rid = saveCourse("必修" + i, "周五 08:00-09:40", 2, "公共必修");
            Enrollment re = new Enrollment();
            re.setStudentId(sid);
            re.setCourseId(rid);
            re.setRound(1);
            re.setPoints(0);
            re.setStatus("approved");
            enrollmentRepo.save(re);
        }
        assertEquals(8, countApproved(), "前置：8 门必修已录取");

        Long e1 = saveCourse("选修一", "周一 08:00-09:40");
        Long e2 = saveCourse("选修二", "周二 08:00-09:40");
        Long e3 = saveCourse("选修三", "周三 08:00-09:40");

        // 8 门必修不占额度：第 1、2 门选修应成功
        Map<String, Object> r1 = enrollmentService.enroll(sid, e1, 0);
        assertEquals(true, r1.get("success"), "8 门必修不应占用选修额度，第 1 门选修应成功：" + r1.get("msg"));
        Map<String, Object> r2 = enrollmentService.enroll(sid, e2, 0);
        assertEquals(true, r2.get("success"), "第 2 门选修应成功：" + r2.get("msg"));

        // 第 3 门选修被拒，提示为选修门数上限
        Map<String, Object> r3 = enrollmentService.enroll(sid, e3, 0);
        assertEquals(false, r3.get("success"), "第 3 门选修应被拒绝：" + r3.get("msg"));
        assertTrue(String.valueOf(r3.get("msg")).contains("选修课"), "提示应说明选修门数上限：" + r3.get("msg"));

        // 新增 1 门必修（第 9 门）：选修额度不受影响，仍报满
        Long rid9 = saveCourse("必修九", "周五 10:00-11:40", 2, "公共必修");
        Enrollment re9 = new Enrollment();
        re9.setStudentId(sid);
        re9.setCourseId(rid9);
        re9.setRound(1);
        re9.setPoints(0);
        re9.setStatus("approved");
        enrollmentRepo.save(re9);
        Map<String, Object> r4 = enrollmentService.enroll(sid, e3, 0);
        assertEquals(false, r4.get("success"), "新增必修不改变额度，第 3 门选修仍应被拒绝：" + r4.get("msg"));

        // 退掉 1 门选修（必修退课在产品上不可用，此处验证选修退课回退额度）
        Map<String, Object> drop = enrollmentService.drop(sid, e1);
        assertEquals(true, drop.get("success"), "退选修应成功：" + drop.get("msg"));
        Map<String, Object> r5 = enrollmentService.enroll(sid, e3, 0);
        assertEquals(true, r5.get("success"), "退掉 1 门选修后额度回退，第 3 门选修应成功：" + r5.get("msg"));

        // 最终：9 门必修 + 2 门选修
        long electives = enrollmentRepo.findByStudentId(sid).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .map(e -> courseRepo.findById(e.getCourseId()).orElse(null))
                .filter(c -> c != null && !"公共必修".equals(c.getCategory()) && !"专业必修".equals(c.getCategory()))
                .count();
        assertEquals(2, electives, "最终选修应为 2 门（必修不计入）");
    }

    /* ================= 工具 ================= */

    private void concurrentEnroll(List<Long> cids) throws Exception {
        int n = cids.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger okCount = new AtomicInteger();

        for (Long cid : cids) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    Map<String, Object> r = enrollmentService.enroll(sid, cid, 0);
                    if (Boolean.TRUE.equals(r.get("success"))) okCount.incrementAndGet();
                } catch (Exception ignored) { }
                return null;
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发报名线程应全部结束");
    }

    private long countApproved() {
        return enrollmentRepo.findByStudentId(sid).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .count();
    }

    private int totalCredits() {
        int sum = 0;
        for (Enrollment e : enrollmentRepo.findByStudentId(sid)) {
            if (!"approved".equals(e.getStatus())) continue;
            sum += courseRepo.findById(e.getCourseId()).map(Course::getCredit).orElse(0);
        }
        return sum;
    }

    private Long saveCourse(String name, String time) { return saveCourse(name, time, 2); }

    private Long saveCourse(String name, String time, int credit) { return saveCourse(name, time, credit, "专业选修"); }

    private Long saveCourse(String name, String time, int credit, String category) {
        Course c = new Course();
        c.setName(name);
        c.setCategory(category);
        c.setCapacity(10);
        c.setCredit(credit);
        c.setTime(time);
        return courseRepo.save(c).getId();
    }
}
