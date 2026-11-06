package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.GradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * 【规则二验证】毕业学分要求：必修全过 + 非必修各类达标。
 * <p>
 * 事实说明（写测试前已确认的当前实现）：
 * - 毕业审核只统计 status=approved（已录取）的学分，从不读取成绩表（Grade），即没有“已通过/已结束”的概念；
 * - 必修只按“必修类学分总数 ≥ gradReqRequired”判断，没有“每门必修课都必须通过”的逐门校验；
 * - 系统没有“科技学分”类别，实际类别为 必修/选修/通识/实践（CourseGroup 固定映射），本测试以“选修”类模拟规则中的“科技学分”。
 * 断言按“规则应然”编写，不符合规则的用例会失败并在报告中如实记录。
 */
@ExtendWith(MockitoExtension.class)
class Rule2GraduationTest {

    @Mock private EnrollmentService enrollmentService;
    @Mock private CourseService courseService;
    @Mock private EnrollmentRepository enrollmentRepo;
    @Mock private GradeRepository gradeRepo;

    @InjectMocks private GraduationService graduationService;

    private SystemConfig config;

    @BeforeEach
    void setUp() {
        // 规则二.3：本次验证按“每类 4 分”配置
        config = new SystemConfig();
        config.setId(1L);
        config.setGradReqRequired(4);
        config.setGradReqElective(4);
        config.setGradReqGeneral(4);
        config.setGradReqPractice(4);

        lenient().when(enrollmentService.ensureRequiredEnrollments(anyLong())).thenReturn(0);
        lenient().when(enrollmentService.getSystemConfig()).thenReturn(config);
        lenient().when(courseService.findAll()).thenReturn(List.of());
        lenient().when(courseService.teacherName(any())).thenReturn("");
    }

    /* ================= 判定辅助：从 summary 推导“是否满足毕业要求” ================= */

    @SuppressWarnings("unchecked")
    private boolean graduated(Long sid) {
        Map<String, Object> sum = graduationService.summary(sid);
        Map<String, Object> total = (Map<String, Object>) sum.get("total");
        List<Map<String, Object>> cats = (List<Map<String, Object>>) sum.get("categories");
        boolean allDone = cats.stream().allMatch(c -> Boolean.TRUE.equals(c.get("done")));
        return Integer.valueOf(0).equals(total.get("gap")) && allDone;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> category(Long sid, String key) {
        Map<String, Object> sum = graduationService.summary(sid);
        List<Map<String, Object>> cats = (List<Map<String, Object>>) sum.get("categories");
        return cats.stream().filter(c -> key.equals(c.get("key"))).findFirst().orElseThrow();
    }

    /* ================= 用例 ================= */

    /** 场景 2.1：必修全部通过 + 非必修各类均达标 → 应判定毕业 */
    @Test
    void R2_allPassed_allCategoriesMet_graduates() {
        Long sid = 100L;
        stubCourses(sid, List.of(
                course(1L, "必修A", "专业必修", 4),
                course(2L, "选修A", "专业选修", 4),
                course(3L, "通识A", "通识选修", 4),
                course(4L, "实践A", "实践选修", 4)));

        stubGrades(sid, 1L, 2L, 3L, 4L);

        assertTrue(graduated(sid), "必修全过且各类 4 分达标，应判定满足毕业要求");
    }

    /** 场景 2.2：选修类（模拟“科技学分”）差 1 分（系统学分为整数，无法构造 0.5 分，以 3/4 等价验证边界不达标）→ 不应判定毕业 */
    @Test
    void R2_electiveShortByOne_notGraduates() {
        Long sid = 100L;
        stubCourses(sid, List.of(
                course(1L, "必修A", "专业必修", 4),
                course(2L, "选修A", "专业选修", 3),   // 差 1 分
                course(3L, "通识A", "通识选修", 4),
                course(4L, "实践A", "实践选修", 4)));

        stubGrades(sid, 1L, 2L, 3L, 4L);

        assertFalse(graduated(sid), "选修类差 1 分未达标，不应判定毕业");
        Map<String, Object> elective = category(sid, "选修");
        assertEquals(1, elective.get("gap"), "选修类缺口应为 1 分");
        assertEquals(false, elective.get("done"));
    }

    /** 场景 2.3：非必修全部达标，但教学计划中另有一门必修 B 未通过（未选）→ 不应判定毕业（规则断言） */
    @Test
    void R2_requiredCourseMissing_notGraduates_byRule() {
        Long sid = 100L;
        // 必修 A 已过（4 分，满足必修类 4 分要求）；必修 B 属于教学计划但学生未修
        Course requiredB = course(11L, "必修B", "专业必修", 4);
        List<Course> approvedOnes = List.of(
                course(1L, "必修A", "专业必修", 4),
                course(2L, "选修A", "专业选修", 4),
                course(3L, "通识A", "通识选修", 4),
                course(4L, "实践A", "实践选修", 4));
        lenient().when(courseService.findAll()).thenReturn(
                new java.util.ArrayList<>(approvedOnes) {{ add(requiredB); }});
        stubApproved(sid, approvedOnes.stream().map(Course::getId).toList());
        for (Course c : approvedOnes) lenient().when(courseService.findById(c.getId())).thenReturn(c);

        stubGrades(sid, 1L, 2L, 3L, 4L);

        assertFalse(graduated(sid),
                "按规则【所有必修课必须全部通过】，必修 B 未修不应判定毕业；"
                        + "当前实现只看必修类学分总数（4≥4 即达标），会把该学生判为已满足毕业要求");
    }

    /** 场景 2.4：必修课“已选未结束”（approved 但无成绩/未通过）→ 不应判定毕业（规则断言） */
    @Test
    void R2_requiredApprovedButNotFinished_notGraduates_byRule() {
        Long sid = 100L;
        stubCourses(sid, List.of(
                course(1L, "必修A", "专业必修", 4),      // 已选未结束：approved，但没有任何成绩记录
                course(2L, "选修A", "专业选修", 4),
                course(3L, "通识A", "通识选修", 4),
                course(4L, "实践A", "实践选修", 4)));

        stubGrades(sid, 2L, 3L, 4L);

        assertFalse(graduated(sid),
                "按规则【只统计已通过课程】，必修 A 已选未结束（无成绩）不应计入；"
                        + "当前实现按 approved 直接计满 4 分，会把该学生判为已满足毕业要求");
    }

    /** 场景 2.5：某一非必修类别完全没有课程 → 不应判定毕业 */
    @Test
    void R2_emptyCategory_notGraduates() {
        Long sid = 100L;
        stubCourses(sid, List.of(
                course(1L, "必修A", "专业必修", 4),
                course(2L, "选修A", "专业选修", 4),
                course(3L, "通识A", "通识选修", 4)));   // 实践类无课

        stubGrades(sid, 1L, 2L, 3L);

        assertFalse(graduated(sid), "实践类完全没有课程（要求 4 分），不应判定毕业");
        Map<String, Object> practice = category(sid, "实践");
        assertEquals(false, practice.get("done"));
    }

    /** 场景 2.6：必修课学分不应计入“选修/科技学分”类汇总 */
    @Test
    void R2_requiredCredits_notCountedIntoElective() {
        Long sid = 100L;
        stubCourses(sid, List.of(
                course(1L, "必修A", "专业必修", 4),
                course(2L, "选修A", "专业选修", 4)));

        stubGrades(sid, 1L, 2L);

        Map<String, Object> elective = category(sid, "选修");
        Map<String, Object> required = category(sid, "必修");
        assertEquals(4, elective.get("earned"), "选修类只应含选修课的 4 分，不应混入必修学分");
        assertEquals(4, required.get("earned"), "必修学分应归入必修类");
    }

    /** 场景 2.7：已退课（dropped）的记录不应计入已获学分（当前实现按 approved 过滤，应通过） */
    @Test
    void R2_droppedCourse_notCounted() {
        Long sid = 100L;
        Course dropped = course(2L, "退选课", "专业选修", 4);
        Enrollment d = enrollment(sid, dropped);
        d.setStatus("dropped");
        Course kept = course(3L, "在选课", "专业选修", 4);
        lenient().when(courseService.findById(2L)).thenReturn(dropped);
        lenient().when(courseService.findById(3L)).thenReturn(kept);
        lenient().when(enrollmentRepo.findByStudentId(sid)).thenReturn(List.of(d, enrollment(sid, kept)));

        stubGrades(sid, 3L);

        Map<String, Object> elective = category(sid, "选修");
        assertEquals(4, elective.get("earned"), "已退课记录不应计入已获学分");
    }

    /* ================= 造数 ================= */

    /** 给指定课程出具通过成绩（总评 88 分 ≥ 60 视为已通过） */
    private void stubGrades(Long sid, Long... courseIds) {
        List<Grade> grades = new ArrayList<>();
        for (Long cid : courseIds) {
            Grade g = new Grade();
            g.setStudentId(sid);
            g.setCourseId(cid);
            g.setRegularScore(80.0);
            g.setFinalScore(92.0);
            g.setTotalScore(88.0);
            grades.add(g);
        }
        lenient().when(gradeRepo.findByStudentId(sid)).thenReturn(grades);
    }

    private void stubCourses(Long sid, List<Course> courses) {
        stubApproved(sid, courses.stream().map(Course::getId).toList());
        for (Course c : courses) {
            lenient().when(courseService.findById(c.getId())).thenReturn(c);
        }
    }

    private void stubApproved(Long sid, List<Long> courseIds) {
        List<Enrollment> list = new ArrayList<>();
        for (Long cid : courseIds) list.add(enrollment(sid, cid));
        lenient().when(enrollmentRepo.findByStudentId(sid)).thenReturn(list);
    }

    private static Enrollment enrollment(Long sid, Course c) { return enrollment(sid, c.getId()); }

    private static Enrollment enrollment(Long sid, Long courseId) {
        Enrollment e = new Enrollment();
        e.setStudentId(sid);
        e.setCourseId(courseId);
        e.setRound(1);
        e.setPoints(0);
        e.setStatus("approved");
        return e;
    }

    private static Course course(Long id, String name, String category, int credit) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        c.setCategory(category);
        c.setCredit(credit);
        c.setCapacity(10);
        return c;
    }
}
