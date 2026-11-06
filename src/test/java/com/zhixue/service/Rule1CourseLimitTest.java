package com.zhixue.service;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 【规则一验证】每学期最多选 2 门课 —— 正式选课 / 候补递补 / 投点结算 三个入口。
 * 断言按“规则应然”编写：若当前实现不符合规则，用例失败并在验证报告中如实记录。
 * 课程时间约定：已录取课程统一“周一 08:00-09:40”，被报名的新课统一“周四 10:00-11:40”，避免误触时间冲突。
 */
@ExtendWith(MockitoExtension.class)
class Rule1CourseLimitTest {

    @Mock private EnrollmentRepository enrollmentRepo;
    @Mock private CourseRepository courseRepo;
    @Mock private UserRepository userRepo;
    @Mock private SystemConfigRepository sysRepo;
    @Mock private WaitlistRepository waitlistRepo;
    @Mock private WaitlistService waitlistService;
    @Mock private NotificationService notificationService;
    @Mock private RoundScheduleService roundScheduleService;
    @Mock private StaggerService staggerService;

    @InjectMocks private EnrollmentService enrollService;
    @InjectMocks private WaitlistService waitlistSvc;

    private SystemConfig config;

    @BeforeEach
    void setUp() {
        config = new SystemConfig();
        config.setId(1L);
        config.setCurrentRound(1);
        config.setRoundOpen(true);
        config.setMaxCreditPerSemester(24);
        config.setMaxCoursesPerSemester(2);   // 门数上限：只统计非必修课

        lenient().when(roundScheduleService.sync()).thenReturn(config);
        lenient().when(sysRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(config));
        lenient().when(sysRepo.findById(1L)).thenReturn(Optional.of(config));
        lenient().when(enrollmentRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrollmentRepo.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(staggerService.checkGate(anyInt(), any())).thenReturn(null);
    }

    /* ================= 入口一：正式选课 enroll() ================= */

    /** 场景 1.1：已选 1 门，再选 1 门应成功 */
    @Test
    void R1_enroll_secondCourse_shouldSucceed() {
        Long sid = 100L;
        stubStudent(sid);
        Course first = course(1L, "课程一", 3);
        stubApproved(sid, List.of(enrollment(sid, first, "approved")));

        Map<String, Object> r = enrollService.enroll(sid, 2L, 5);

        assertEquals(true, r.get("success"), "已选 1 门时再选 1 门应成功，实际：" + r.get("msg"));
    }

    /** 场景 1.2：已选 2 门选修，再选第 3 门选修应被拒绝并给出明确提示（规则断言） */
    @Test
    void R1_enroll_thirdElective_shouldBeRejected_byRule() {
        Long sid = 100L;
        stubStudent(sid);
        stubApproved(sid, List.of(
                enrollment(sid, course(1L, "选修一", 3), "approved"),
                enrollment(sid, course(2L, "选修二", 3), "approved")));

        Map<String, Object> r = enrollService.enroll(sid, 3L, 5);

        assertEquals(false, r.get("success"),
                "按规则【每学期最多 2 门选修】第 3 门选修应被拒绝；当前实现放行即不符合规则（msg=" + r.get("msg") + "）");
        assertTrue(String.valueOf(r.get("msg")).contains("选修课"), "拒绝提示应说明选修门数上限，实际：" + r.get("msg"));
    }

    /** 场景 1.2b：已有 8 门必修的学生，仍可选 2 门选修；第 3 门选修才被拒（必修不占额度） */
    @Test
    void R1_enroll_requiredCoursesDoNotConsumeQuota() {
        Long sid = 100L;
        stubStudent(sid);
        List<Enrollment> approved = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            approved.add(enrollment(sid, requiredCourse(i, "必修" + i), "approved"));
        }
        // 已录取的查出来是周五必修课；未录取的新选修课用 NEW_TIME 避免冲突
        lenient().when(enrollmentRepo.findByStudentIdAndRound(sid, 1)).thenReturn(approved);
        lenient().when(enrollmentRepo.findByStudentId(sid)).thenReturn(approved);
        lenient().when(enrollmentRepo.findByStudentIdAndCourseIdAndRound(eq(sid), anyLong(), eq(1)))
                .thenReturn(Optional.empty());
        lenient().when(courseRepo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0, Long.class);
            if (id <= 8L) return Optional.of(requiredCourse(id, "必修" + id));
            boolean isApproved = approved.stream().anyMatch(e -> id.equals(e.getCourseId()));
            return Optional.of(course(id, "选修" + id, 3, isApproved ? APPROVED_TIME : NEW_TIME));
        });

        Map<String, Object> r1 = enrollService.enroll(sid, 101L, 5);
        assertEquals(true, r1.get("success"), "8 门必修不应占用选修额度，第 1 门选修应成功：" + r1.get("msg"));

        // 模拟第一门选修录取后，第二门仍可报
        approved.add(enrollment(sid, course(101L, "选修一", 3), "approved"));
        Map<String, Object> r2 = enrollService.enroll(sid, 102L, 5);
        assertEquals(true, r2.get("success"), "已选 8 门必修 + 1 门选修时，第 2 门选修应成功：" + r2.get("msg"));

        approved.add(enrollment(sid, course(102L, "选修二", 3), "approved"));
        Map<String, Object> r3 = enrollService.enroll(sid, 103L, 5);
        assertEquals(false, r3.get("success"), "8 门必修 + 2 门选修时，第 3 门选修应被拒绝：" + r3.get("msg"));
        assertTrue(String.valueOf(r3.get("msg")).contains("选修课"), "提示应为选修门数上限：" + r3.get("msg"));
    }

    /** 场景 1.2c：必修学分不占选修学分上限——已修必修 16 分远超上限 10 分，仍可报名选修 */
    @Test
    void R1_enroll_requiredCreditsDoNotConsumeCreditLimit() {
        Long sid = 100L;
        stubStudent(sid);
        config.setMaxCreditPerSemester(10);
        List<Enrollment> approved = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            approved.add(enrollment(sid, requiredCourse(i, "必修" + i), "approved")); // 8 门必修 × 2 学分 = 16 分
        }
        stubApproved(sid, approved);
        lenient().when(courseRepo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0, Long.class);
            if (id <= 8L) return Optional.of(requiredCourse(id, "必修" + id));
            return Optional.of(course(id, "选修" + id, 3, NEW_TIME));
        });

        Map<String, Object> r = enrollService.enroll(sid, 101L, 5);

        assertEquals(true, r.get("success"),
                "必修学分（16 分）不应占选修学分上限（10 分），报名选修应成功：" + r.get("msg"));
    }

    /** 场景 1.3（对照）：上限来自配置——当前实现为“学分上限”且从 SystemConfig 读取，未写死 */
    @Test
    void R1_enroll_limitIsReadFromConfig_creditBased() {
        Long sid = 100L;
        stubStudent(sid);
        config.setMaxCreditPerSemester(4);
        stubApproved(sid, List.of(enrollment(sid, course(1L, "课程一", 3), "approved")));

        Map<String, Object> r = enrollService.enroll(sid, 2L, 5);

        assertEquals(false, r.get("success"), "学分上限应从配置读取并生效");
        assertTrue(String.valueOf(r.get("msg")).contains("学分上限"), "提示应说明学分上限，实际：" + r.get("msg"));
    }

    /* ================= 入口二：候补递补确认 confirm() ================= */

    /** 场景 1.4：已录取 2 门选修后，候补递补确认应被拒绝（规则断言） */
    @Test
    void R1_waitlistConfirm_beyondLimit_shouldBeRejected_byRule() {
        Long sid = 100L;
        Waitlist w = new Waitlist();
        w.setId(9L);
        w.setStudentId(sid);
        w.setCourseId(3L);
        w.setRound(1);
        w.setStatus("confirming");
        w.setDeadline(LocalDateTime.now().plusMinutes(20));

        User stu = new User();
        stu.setId(sid); stu.setNo("S" + sid); stu.setName("学生" + sid);
        stu.setRole("student"); stu.setGrade(2023);
        lenient().when(userRepo.findByIdForUpdate(sid)).thenReturn(Optional.of(stu));
        lenient().when(waitlistRepo.findById(9L)).thenReturn(Optional.of(w));
        lenient().when(waitlistRepo.save(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(courseRepo.findById(3L)).thenReturn(Optional.of(course(3L, "课程三", 3, NEW_TIME)));
        // 门数统计需逐门查课程：两门已录取选修
        lenient().when(courseRepo.findById(1L)).thenReturn(Optional.of(course(1L, "选修一", 3)));
        lenient().when(courseRepo.findById(2L)).thenReturn(Optional.of(course(2L, "选修二", 3)));
        // confirm 内部的时间冲突检查：已录取 2 门选修（周一），新课（周四）不冲突
        List<Enrollment> approved = List.of(
                enrollment(sid, course(1L, "选修一", 3), "approved"),
                enrollment(sid, course(2L, "选修二", 3), "approved"));
        lenient().when(enrollmentRepo.findByStudentIdAndRound(sid, 1)).thenReturn(approved);
        lenient().when(enrollmentRepo.findByStudentId(sid)).thenReturn(approved);
        lenient().when(enrollmentRepo.findByStudentIdAndCourseIdAndRound(sid, 3L, 1))
                .thenReturn(Optional.empty());

        Map<String, Object> r = waitlistSvc.confirm(9L, sid);

        assertEquals(false, r.get("success"),
                "按规则【每学期最多 2 门选修】已选 2 门选修后候补确认应被拒绝；当前实现放行即不符合规则（msg=" + r.get("msg") + "）");
        assertTrue(String.valueOf(r.get("msg")).contains("选修课"), "提示应说明选修门数上限：" + r.get("msg"));
    }

    /** 场景 1.4b：大量必修 + 2 门选修时，候补确认只按选修数计——8 门必修不阻断第 2 门选修递补 */
    @Test
    void R1_waitlistConfirm_requiredCoursesDoNotConsumeQuota() {
        Long sid = 100L;
        Waitlist w = new Waitlist();
        w.setId(10L);
        w.setStudentId(sid);
        w.setCourseId(3L);
        w.setRound(1);
        w.setStatus("confirming");
        w.setDeadline(LocalDateTime.now().plusMinutes(20));

        User stu = new User();
        stu.setId(sid); stu.setNo("S" + sid); stu.setName("学生" + sid);
        stu.setRole("student"); stu.setGrade(2023);
        lenient().when(userRepo.findByIdForUpdate(sid)).thenReturn(Optional.of(stu));
        lenient().when(waitlistRepo.findById(10L)).thenReturn(Optional.of(w));
        lenient().when(waitlistRepo.save(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(courseRepo.findById(3L)).thenReturn(Optional.of(course(3L, "选修三", 3, NEW_TIME)));

        // 8 门必修（id 10~17，统一周五，避开选修时段）+ 1 门选修（id 1，周一），新课（id 3，周四）互不冲突
        List<Enrollment> approved = new ArrayList<>();
        for (long i = 10; i <= 17; i++) {
            approved.add(enrollment(sid, requiredCourse(i, "必修" + (i - 9)), "approved"));
        }
        approved.add(enrollment(sid, course(1L, "选修一", 3), "approved"));
        lenient().when(enrollmentRepo.findByStudentIdAndRound(sid, 1)).thenReturn(approved);
        lenient().when(enrollmentRepo.findByStudentId(sid)).thenReturn(approved);
        lenient().when(enrollmentRepo.findByStudentIdAndCourseIdAndRound(sid, 3L, 1))
                .thenReturn(Optional.empty());
        lenient().when(courseRepo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0, Long.class);
            if (id == 3L) return Optional.of(course(3L, "选修三", 3, NEW_TIME));
            if (id == 1L) return Optional.of(course(1L, "选修一", 3, APPROVED_TIME));
            if (id >= 10L && id <= 17L) return Optional.of(requiredCourse(id, "必修" + (id - 9)));
            return Optional.empty();
        });

        Map<String, Object> r = waitlistSvc.confirm(10L, sid);

        assertEquals(true, r.get("success"),
                "必修不占门数额度：8 门必修 + 1 门选修时，第 2 门选修候补确认应成功（msg=" + r.get("msg") + "）");
    }

    /* ================= 入口三：投点结算 settleRound() ================= */

    /** 场景 1.5：投点结算后中选超过 2 门选修，只应保留积分最高的 2 门（规则断言） */
    @Test
    void R1_settle_moreThanTwoWins_keepsHighestTwo_byRule() {
        Long sid = 100L;
        Course c1 = course(1L, "选修一", 3);
        Course c2 = course(2L, "选修二", 3);
        Course c3 = course(3L, "选修三", 3);
        c1.setTime("周一 08:00-09:40"); c2.setTime("周二 08:00-09:40"); c3.setTime("周三 08:00-09:40");
        Enrollment high = enrollment(sid, c1, "pending"); high.setPoints(50);
        Enrollment mid = enrollment(sid, c2, "pending"); mid.setPoints(30);
        Enrollment low = enrollment(sid, c3, "pending"); low.setPoints(10);

        stubRound(List.of(c1, c2, c3), List.of(high, mid, low));
        enrollService.settleRound(1);

        long approvedCount = List.of(high, mid, low).stream()
                .filter(e -> "approved".equals(e.getStatus())).count();
        assertTrue(approvedCount <= 2,
                "按规则中选修不得超过 2 门（保留积分最高 2 门）；实际中选 " + approvedCount + " 门");
        assertEquals("rejected", low.getStatus(), "积分最低的一门应被拒（保留积分最高的 2 门）");
    }

    /** 场景 1.5b：结算时已录取 8 门必修不占门数额度，3 门选修候选仍按积分保留 2 门 */
    @Test
    void R1_settle_requiredApprovedDoNotConsumeQuota() {
        Long sid = 100L;
        List<Course> courses = new ArrayList<>();
        List<Enrollment> rows = new ArrayList<>();
        // 8 门必修：跨轮次已录取（系统自动排课），不占选修额度
        for (long i = 1; i <= 8; i++) {
            Course rc = requiredCourse(i, "必修" + i);
            courses.add(rc);
            rows.add(enrollment(sid, rc, "approved"));
        }
        Course c1 = course(101L, "选修一", 3);
        Course c2 = course(102L, "选修二", 3);
        Course c3 = course(103L, "选修三", 3);
        c1.setTime("周一 08:00-09:40"); c2.setTime("周二 08:00-09:40"); c3.setTime("周三 08:00-09:40");
        Enrollment high = enrollment(sid, c1, "pending"); high.setPoints(50);
        Enrollment mid = enrollment(sid, c2, "pending"); mid.setPoints(30);
        Enrollment low = enrollment(sid, c3, "pending"); low.setPoints(10);
        courses.add(c1); courses.add(c2); courses.add(c3);
        rows.add(high); rows.add(mid); rows.add(low);

        stubRound(courses, rows);
        enrollService.settleRound(1);

        assertEquals("approved", high.getStatus(), "8 门必修不应占用选修额度，最高分选修应录取");
        assertEquals("approved", mid.getStatus(), "第 2 门选修应录取");
        assertEquals("rejected", low.getStatus(), "第 3 门选修应因选修门数上限被拒");
    }

    /** 场景 1.5c：必修课“新增/退课”不影响选修额度——必修数从 0 变 8，选修仍能录取 2 门（见 1.5 与 1.5b 对照） */
    @Test
    void R1_settle_requiredCountChange_doesNotAffectElectiveQuota() {
        Long sid = 100L;
        // 对照组：无任何必修，2 门选修应全部录取
        Course a1 = course(201L, "选修A", 3);
        Course a2 = course(202L, "选修B", 3);
        a1.setTime("周一 08:00-09:40"); a2.setTime("周二 08:00-09:40");
        Enrollment ea1 = enrollment(sid, a1, "pending"); ea1.setPoints(40);
        Enrollment ea2 = enrollment(sid, a2, "pending"); ea2.setPoints(20);
        stubRound(List.of(a1, a2), List.of(ea1, ea2));
        enrollService.settleRound(1);
        assertEquals("approved", ea1.getStatus());
        assertEquals("approved", ea2.getStatus(), "无必修时 2 门选修应全部录取");

        // 实验组：同样 2 门选修候选，另加 8 门已录取必修，结果应完全一致
        Long sid2 = 200L;
        List<Course> courses = new ArrayList<>();
        List<Enrollment> rows = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            Course rc = requiredCourse(i + 300, "必修" + i);
            courses.add(rc);
            rows.add(enrollment(sid2, rc, "approved"));
        }
        Course b1 = course(401L, "选修A2", 3);
        Course b2 = course(402L, "选修B2", 3);
        b1.setTime("周一 08:00-09:40"); b2.setTime("周二 08:00-09:40");
        Enrollment eb1 = enrollment(sid2, b1, "pending"); eb1.setPoints(40);
        Enrollment eb2 = enrollment(sid2, b2, "pending"); eb2.setPoints(20);
        courses.add(b1); courses.add(b2);
        rows.add(eb1); rows.add(eb2);
        stubRound(courses, rows);
        enrollService.settleRound(1);
        assertEquals("approved", eb1.getStatus(), "新增必修后，选修录取结果不应受影响");
        assertEquals("approved", eb2.getStatus(), "新增必修后，选修录取结果不应受影响");
    }

    /** 场景 1.6（对照）：结算按“学分上限”裁决时保留积分最高的课（当前实现的真实语义） */
    @Test
    void R1_settle_creditLimit_keepsHighest_actualBehavior() {
        Long sid = 100L;
        config.setMaxCreditPerSemester(6);
        Course c1 = course(1L, "课程一", 3);
        Course c2 = course(2L, "课程二", 3);
        Course c3 = course(3L, "课程三", 3);
        c1.setTime("周一 08:00-09:40"); c2.setTime("周二 08:00-09:40"); c3.setTime("周三 08:00-09:40");
        Enrollment high = enrollment(sid, c1, "pending"); high.setPoints(50);
        Enrollment mid = enrollment(sid, c2, "pending"); mid.setPoints(30);
        Enrollment low = enrollment(sid, c3, "pending"); low.setPoints(10);

        stubRound(List.of(c1, c2, c3), List.of(high, mid, low));
        enrollService.settleRound(1);

        assertEquals("approved", high.getStatus());
        assertEquals("approved", mid.getStatus());
        assertEquals("rejected", low.getStatus(), "学分上限 6 分：3+3 已满，最低分第 3 门被拒（积分高者优先保留）");
    }

    /* ================= 造数 ================= */

    /** 时间约定：已录取课程 = 周一 08:00-09:40；将要报名的新课 = 周四 10:00-11:40（互不冲突） */
    private static final String APPROVED_TIME = "周一 08:00-09:40";
    private static final String NEW_TIME = "周四 10:00-11:40";

    private void stubStudent(Long sid) {
        User u = new User();
        u.setId(sid);
        u.setNo("S" + sid);
        u.setName("学生" + sid);
        u.setRole("student");
        u.setGrade(2023);
        lenient().when(userRepo.findByIdForUpdate(sid)).thenReturn(Optional.of(u));
        // enroll() 锁定将要报名的新课（统一 NEW_TIME，避免误触时间冲突）
        lenient().when(courseRepo.findByIdForUpdate(anyLong()))
                .thenAnswer(inv -> Optional.of(
                        course(inv.getArgument(0, Long.class), "新课", 3, NEW_TIME)));
    }

    private void stubApproved(Long sid, List<Enrollment> approved) {
        List<Enrollment> all = new ArrayList<>(approved);
        lenient().when(enrollmentRepo.findByStudentIdAndRound(sid, 1)).thenReturn(all);
        lenient().when(enrollmentRepo.findByStudentId(sid)).thenReturn(all);
        lenient().when(enrollmentRepo.findByStudentIdAndCourseIdAndRound(eq(sid), anyLong(), eq(1)))
                .thenReturn(Optional.empty());
        // 冲突/学分统计逐条查课程：已录取 → APPROVED_TIME，其余 → NEW_TIME
        lenient().when(courseRepo.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0, Long.class);
            boolean isApproved = all.stream().anyMatch(e -> id.equals(e.getCourseId()));
            return Optional.of(course(id, "课程" + id, 3, isApproved ? APPROVED_TIME : NEW_TIME));
        });
    }

    private void stubRound(List<Course> courses, List<Enrollment> enrollments) {
        when(courseRepo.findAll()).thenReturn(new ArrayList<>(courses));
        when(enrollmentRepo.findByRound(1)).thenReturn(new ArrayList<>(enrollments));
        List<Enrollment> all = new ArrayList<>(enrollments);
        when(enrollmentRepo.findByStudentId(anyLong())).thenAnswer(inv ->
                all.stream().filter(e -> inv.getArgument(0, Long.class).equals(e.getStudentId())).toList());
    }

    private static Course course(Long id, String name, int credit) {
        return course(id, name, credit, APPROVED_TIME);
    }

    private static Course course(Long id, String name, int credit, String time) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        c.setCapacity(10);
        c.setCredit(credit);
        c.setTime(time);
        c.setCategory("专业选修");
        return c;
    }

    /** 必修课造数：公共必修、周五上课（避开选修的周一~周四）、2 学分 */
    private static Course requiredCourse(Long id, String name) {
        Course c = course(id, name, 2, "周五 08:00-09:40");
        c.setCategory("公共必修");
        return c;
    }

    private static Enrollment enrollment(Long sid, Course c, String status) {
        Enrollment e = new Enrollment();
        e.setStudentId(sid);
        e.setCourseId(c.getId());
        e.setRound(1);
        e.setPoints(0);
        e.setStatus(status);
        return e;
    }
}
