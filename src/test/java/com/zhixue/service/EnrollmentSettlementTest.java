package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 结算规则测试：重点覆盖“报名阶段无法发现、只有结算时才暴露”的两类冲突。
 * 这些测试不启动 Spring，只验证 EnrollmentService.settleRound 的分配逻辑。
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentSettlementTest {

    @Mock private EnrollmentRepository enrollmentRepo;
    @Mock private CourseRepository courseRepo;
    @Mock private UserRepository userRepo;
    @Mock private SystemConfigRepository sysRepo;
    @Mock private WaitlistService waitlistService;
    @Mock private NotificationService notificationService;
    @Mock private RoundScheduleService roundScheduleService;
    @Mock private StaggerService staggerService;

    @InjectMocks private EnrollmentService service;

    private SystemConfig config;

    @BeforeEach
    void setUp() {
        config = new SystemConfig();
        config.setId(1L);
        config.setCurrentRound(1);
        config.setRoundOpen(true);
        config.setConfirmMinutes(30);
        config.setMaxCreditPerSemester(24);

        lenient().when(roundScheduleService.sync()).thenReturn(config);
        lenient().when(sysRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(config));
        lenient().when(enrollmentRepo.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void conflictingCourses_onlyHigherBidIsAdmitted() {
        Course a = course(1L, "课程A", 10, 3, "周一 08:00-09:40");
        Course b = course(2L, "课程B", 10, 3, "周一 09:00-10:40");
        Enrollment high = enrollment(100L, 1L, 50);
        Enrollment low = enrollment(100L, 2L, 40);

        stubRound(List.of(a, b), List.of(high, low));
        Map<String, Object> result = service.settleRound(1);

        assertEquals("approved", high.getStatus());
        assertEquals("rejected", low.getStatus());
        assertEquals(1, data(result).get("admitted"));
        assertEquals(1, data(result).get("rejected"));
        assertTrue(rejectReasons(result, 2L).containsKey("与已录取课程时间冲突"));
    }

    @Test
    void creditLimitCheck_countsCreditsAdmittedInThisSettlement() {
        Course a = course(1L, "课程A", 10, 3, "周一 08:00-09:40");
        Course b = course(2L, "课程B", 10, 3, "周二 08:00-09:40");
        config.setMaxCreditPerSemester(4);
        Enrollment high = enrollment(100L, 1L, 50);
        Enrollment low = enrollment(100L, 2L, 40);

        stubRound(List.of(a, b), List.of(high, low));
        Map<String, Object> result = service.settleRound(1);

        assertEquals("approved", high.getStatus());
        assertEquals("rejected", low.getStatus());
        assertTrue(rejectReasons(result, 2L).keySet().stream()
                .anyMatch(k -> k.contains("学分上限")));
    }

    @Test
    void creditLimitCheck_excludesRequiredCourses() {
        Course required = course(10L, "高等数学", 100, 4, "周三 08:00-09:40");
        required.setCategory("专业必修");
        Course elective = course(1L, "课程A", 10, 3, "周一 08:00-09:40");
        config.setMaxCreditPerSemester(6);

        Enrollment requiredEnr = enrollment(100L, 10L, 0);
        requiredEnr.setStatus("approved");
        Enrollment pending = enrollment(100L, 1L, 50);

        stubRound(List.of(required, elective), List.of(requiredEnr, pending));
        Map<String, Object> result = service.settleRound(1);

        // 新口径：必修学分由教学计划自动安排，不占选修学分上限（必修 4 分 + 选修 3 分 = 7 > 6 也不再拒绝）
        assertEquals("approved", pending.getStatus(),
                "必修学分不应占选修学分上限，选修应正常录取；rejectReasons="
                        + rejectReasons(result, 1L).keySet());
    }

    @Test
    void capacityIsEnforcedByPointsOrder() {
        Course a = course(1L, "课程A", 1, 3, "周一 08:00-09:40");
        Enrollment higher = enrollment(100L, 1L, 50);
        Enrollment lower = enrollment(200L, 1L, 40);

        stubRound(List.of(a), List.of(higher, lower));
        Map<String, Object> result = service.settleRound(1);

        assertEquals("approved", higher.getStatus());
        assertEquals("rejected", lower.getStatus());
        assertTrue(rejectReasons(result, 1L).containsKey("容量已满"));
    }

    @Test
    void duplicatePendingRecords_areRejectedInsteadOfDoubleCounted() {
        Course a = course(1L, "课程A", 10, 3, "周一 08:00-09:40");
        Enrollment first = enrollment(100L, 1L, 50);
        Enrollment duplicate = enrollment(100L, 1L, 30);

        stubRound(List.of(a), List.of(first, duplicate));
        Map<String, Object> result = service.settleRound(1);

        assertEquals("approved", first.getStatus());
        assertEquals("rejected", duplicate.getStatus());
        assertTrue(rejectReasons(result, 1L).containsKey("重复报名"));
    }

    @Test
    void graduationRequirementBlocksExcessElectiveCredits() {
        // 该学生选修类已修满毕业要求(10 分)，再投 3 分课应被拒绝
        Course done = course(1L, "已满课程", 10, 10, "周一 08:00-09:40");
        Enrollment approvedDone = enrollment(100L, 1L, 0);
        approvedDone.setStatus("approved");
        Course extra = course(2L, "多修课程", 10, 3, "周二 08:00-09:40");
        Enrollment bid = enrollment(100L, 2L, 50);
        config.setGradReqElective(10);

        stubRound(List.of(done, extra), List.of(approvedDone, bid));
        Map<String, Object> result = service.settleRound(1);

        assertEquals("approved", approvedDone.getStatus());
        assertEquals("rejected", bid.getStatus());
        assertTrue(rejectReasons(result, 2L).keySet().stream().anyMatch(k -> k.contains("毕业学分")));
    }

    @Test
    void graduationCapCountsCreditsAdmittedInSameSettlement() {
        // 两门 5 分 / 4 分选修，毕业要求 5 分：高分 5 分课先录取后，4 分课被拒
        Course first = course(1L, "课程A", 10, 5, "周一 08:00-09:40");
        Course second = course(2L, "课程B", 10, 4, "周二 08:00-09:40");
        Enrollment high = enrollment(100L, 1L, 50);
        Enrollment low = enrollment(100L, 2L, 40);
        config.setGradReqElective(5);

        stubRound(List.of(first, second), List.of(high, low));
        Map<String, Object> result = service.settleRound(1);

        assertEquals("approved", high.getStatus());
        assertEquals("rejected", low.getStatus());
        assertTrue(rejectReasons(result, 2L).keySet().stream().anyMatch(k -> k.contains("毕业学分")));
    }

    private void stubRound(List<Course> courses, List<Enrollment> enrollments) {
        when(courseRepo.findAll()).thenReturn(new ArrayList<>(courses));
        when(enrollmentRepo.findByRound(1)).thenReturn(new ArrayList<>(enrollments));
        // 结算统计“毕业大类已修学分”会按学生查询全部轮次的录取记录，模拟真实数据库行为
        List<Enrollment> all = new ArrayList<>(enrollments);
        when(enrollmentRepo.findByStudentId(anyLong())).thenAnswer(inv ->
                all.stream()
                        .filter(e -> inv.getArgument(0, Long.class).equals(e.getStudentId()))
                        .toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> result) {
        return (Map<String, Object>) result.get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> rejectReasons(Map<String, Object> result, Long courseId) {
        List<Map<String, Object>> details = (List<Map<String, Object>>) data(result).get("details");
        Map<String, Object> target = details.stream()
                .filter(d -> courseId.equals(d.get("courseId")))
                .findFirst().orElse(null);
        assertNotNull(target, "未找到课程 " + courseId + " 的结算明细");
        return (Map<String, Integer>) target.getOrDefault("rejectReasons", Map.of());
    }

    private static Course course(Long id, String name, int capacity, int credit, String time) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        c.setCapacity(capacity);
        c.setCredit(credit);
        c.setTime(time);
        c.setCategory("专业选修");
        return c;
    }

    private static Enrollment enrollment(Long studentId, Long courseId, int points) {
        Enrollment e = new Enrollment();
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setRound(1);
        e.setPoints(points);
        e.setStatus("pending");
        return e;
    }
}
