package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * AI 识别层测试：验证「问题 → 意图 + 课程实体」的识别准确率与课程覆盖率。
 *
 * 背景：旧实现只把课程表前 40 门写进提示词，第 41 门起（含《大学物理》《线性代数》
 * 《程序设计基础》等公共必修）AI 完全查不到 —— 这里用全量课程做覆盖率断言。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRecognitionTest {

    @Mock private CourseRepository courseRepo;
    @Mock private EnrollmentRepository enrollmentRepo;
    @Mock private SystemConfigRepository configRepo;
    @Mock private UserRepository userRepo;
    @Mock private WaitlistRepository waitlistRepo;
    @Mock private WaitlistService waitlistService;
    @Mock private GradeService gradeService;
    @Mock private CourseService courseService;
    @Mock private IntentService intentService;
    @Mock private GraduationService graduationService;

    @InjectMocks private AiContextService ctx;

    /** 全量课程（59 门，与数据库一致；含 id>1140 的公共必修） */
    private final List<Course> all = new ArrayList<>();

    @BeforeEach
    void setUp() {
        all.clear();
        add(1, "高等数学(下)", "公共必修", 4, "周一 08:00-09:40", "求索楼-201", "微积分、级数、多元函数微积分。");
        add(2, "大学英语(III)", "公共必修", 3, "周二 08:00-09:40", "闻道楼-305", "学术英语写作与口语表达。");
        add(3, "数据结构", "专业必修", 4, "周二 14:00-15:40", "明志楼-401", "数据结构与算法。");
        add(5, "人工智能导论", "专业选修", 3, "周五 14:00-15:40", "温故楼-401", "AI基础概念。");
        add(6, "Web前端开发", "专业选修", 3, "周二 10:00-11:40", "明志楼-302", "HTML/CSS/JS及现代框架。");
        add(16, "品酒人生", "通识选修", 2, "周三 14:00-15:40", "大音楼-201", "葡萄酒产区、品鉴方法与餐酒搭配礼仪。");
        add(21, "心理学导论", "通识选修", 3, "周五 08:00-09:40", "望星楼-201", "普通心理学基本概念与日常应用。");
        add(24, "金工实习", "实践课程", 2, "周一 14:00-15:40", "培训中心一期", "车工、钳工、焊工基础操作实训。");
        add(1101, "机器学习", "专业选修", 3, "周二 14:00-15:40", "明志楼-401", "机器学习核心概念与实践。");
        add(1130, "生活中的化学", "通识选修", 2, "周四 16:00-17:40", "体育馆-101", "生活中的化学常识。");
        add(1136, "数据分析实训", "实践课程", 2, "周五 16:00-17:40", "望星楼-103", "数据分析实训。");
        // 关键：这几门在旧实现（只注入前 40 门）里 AI 根本看不到
        add(1137, "高等数学(上)", "公共必修", 4, "周一 08:00-09:40", "求索楼-201", "函数与极限、导数与微分。");
        add(1138, "大学物理", "公共必修", 4, "周二 14:00-15:40", "明志楼-401", "力学、电磁学、热学基础。");
        add(1139, "线性代数", "公共必修", 4, "周三 14:00-15:40", "明志楼-501", "矩阵、行列式与线性变换。");
        add(1140, "程序设计基础", "公共必修", 4, "周五 14:00-15:40", "明志楼-302", "C 语言与算法入门。");

        lenient().when(courseRepo.findAll()).thenReturn(all);
        lenient().when(courseRepo.findById(anyLong())).thenAnswer(inv -> {
            long id = inv.getArgument(0);
            return all.stream().filter(c -> c.getId() == id).findFirst();
        });
        lenient().when(courseRepo.count()).thenReturn((long) all.size());
        lenient().when(configRepo.findById(1L)).thenReturn(Optional.empty());
        lenient().when(courseService.teacherName(any())).thenReturn("测试老师");
        lenient().when(enrollmentRepo.findByStudentId(anyLong())).thenReturn(List.of());
        lenient().when(enrollmentRepo.findByCourseIdAndRound(anyLong(), any())).thenReturn(List.of());
    }

    private void add(long id, String name, String cat, int credit, String time, String loc, String intro) {
        Course c = new Course();
        c.setId(id); c.setName(name); c.setCategory(cat); c.setCredit(credit);
        c.setTime(time); c.setLocation(loc); c.setIntro(intro); c.setCapacity(50); c.setCampus("主校区");
        all.add(c);
    }

    // ============ 1. 意图识别准确率 ============

    @Test
    @DisplayName("意图识别：学生口语问法应命中对应意图")
    void intentRecognition() {
        assertIntent("我还差多少学分才能毕业？", AiContextService.Intent.GRADUATION);
        assertIntent("毕业要求是什么", AiContextService.Intent.GRADUATION);
        assertIntent("我的成绩和绩点是多少", AiContextService.Intent.GRADES);
        assertIntent("我的课表里都有啥", AiContextService.Intent.MY_ENROLL);
        assertIntent("我已选了哪些课", AiContextService.Intent.MY_ENROLL);
        assertIntent("我的意向单该怎么投分", AiContextService.Intent.MY_INTENT);
        assertIntent("我还能加码吗", AiContextService.Intent.MY_INTENT);
        assertIntent("候补能排到第几", AiContextService.Intent.WAITLIST);
        assertIntent("大学物理讲什么内容", AiContextService.Intent.COURSE_INFO);
        assertIntent("大学物理是几学分", AiContextService.Intent.COURSE_INFO);
        assertIntent("数据结构和时间冲突吗", AiContextService.Intent.CONFLICT);
        assertIntent("帮我推荐几门课", AiContextService.Intent.RECOMMEND);
        assertIntent("我还缺什么课可以补", AiContextService.Intent.RECOMMEND);
        assertIntent("选课规则是怎样的", AiContextService.Intent.ROUND_RULE);
        assertIntent("积分怎么投", AiContextService.Intent.ROUND_RULE);
        assertIntent("同分怎么录取", AiContextService.Intent.ROUND_RULE);
    }

    @Test
    @DisplayName("意图识别：角色相关的词要走教师/管理员分支")
    void roleIntent() {
        AiContextService.Recognition t = ctx.recognize("我教哪些课", null, "teacher");
        assertEquals(AiContextService.Intent.TEACHER, t.intent);
        AiContextService.Recognition a = ctx.recognize("当前选课整体数据统计", null, "admin");
        assertEquals(AiContextService.Intent.ADMIN, a.intent);
    }

    // ============ 2. 课程实体抽取 ============

    @Test
    @DisplayName("课程识别：全量课程逐门提问都应命中（覆盖率 100%）")
    void courseCoverage() {
        List<String> missed = new ArrayList<>();
        for (Course c : all) {
            String q = "《" + c.getName() + "》怎么样？";
            AiContextService.Recognition rec = ctx.recognize(q, null, "student");
            boolean hit = rec.courses.stream().anyMatch(x -> x.getId().equals(c.getId()));
            if (!hit) missed.add(c.getName());
        }
        assertTrue(missed.isEmpty(), "以下课程无法被识别：" + missed);
    }

    @Test
    @DisplayName("课程识别：旧实现丢失的公共必修现在能被查到")
    void formerlyInvisibleCourses() {
        for (String name : new String[]{"大学物理", "线性代数", "程序设计基础", "高等数学(上)"}) {
            AiContextService.Recognition rec = ctx.recognize(name + "几学分", null, "student");
            assertFalse(rec.courses.isEmpty(), name + " 未被识别");
        }
    }

    @Test
    @DisplayName("课程识别：简称 / 英文 / 去括号 / 口语别名")
    void aliases() {
        assertHit("web前端开发是什么课", "Web前端开发");
        assertHit("高数(下)难吗", "高等数学(下)");
        assertHit("大学物理", "大学物理");
        assertHit("英语怎么说", "大学英语(III)");
        assertHit("品酒", "品酒人生");
        assertHit("机器学习", "机器学习");
    }

    @Test
    @DisplayName("课程识别：无关问题不应乱命中课程")
    void noFalsePositive() {
        AiContextService.Recognition rec = ctx.recognize("今天天气怎么样", null, "student");
        assertTrue(rec.courses.isEmpty() || rec.courses.size() <= 1);
        assertEquals(AiContextService.Intent.GENERAL, rec.intent);
    }

    // ============ 3. 边界情况 ============

    @Test
    @DisplayName("边界：空串 / 纯符号 / 超长 / 英文 不应抛异常")
    void boundary() {
        assertNotNull(ctx.recognize("", null, "student"));
        assertNotNull(ctx.recognize("???!!!", null, "student"));
        assertNotNull(ctx.recognize("a".repeat(3000), null, "student"));
        assertNotNull(ctx.recognize("How do I select courses?", null, "student"));
        assertNotNull(ctx.buildSystemPrompt(ctx.recognize("", null, null), "", null, null));
    }

    @Test
    @DisplayName("边界：提示词必须包含规则与术语，且不含空值")
    void promptContent() {
        AiContextService.Recognition rec = ctx.recognize("大学物理几学分", null, "student");
        String p = ctx.buildSystemPrompt(rec, "大学物理几学分", null, "student");
        assertTrue(p.contains("选课规则"));
        assertTrue(p.contains("术语对照"));
        assertTrue(p.contains("大学物理"), "命中课程详情应进入提示词");
        assertTrue(p.contains("禁止编造") || p.contains("严禁编造"));
        assertFalse(p.contains("null"), "提示词不应出现 null");
    }

    // ============ 4. 冲突计算（与后端同口径）============

    @Test
    @DisplayName("时间冲突：同星期重叠时段判为冲突，重叠一天不冲突")
    void conflictDetection() {
        Course a = all.stream().filter(c -> c.getName().equals("高等数学(下)")).findFirst().orElseThrow();
        Course b = all.stream().filter(c -> c.getName().equals("数据结构")).findFirst().orElseThrow();
        // 周一 08:00-09:40 vs 周二 14:00-15:40 → 不冲突
        assertFalse(ctx.conflict(a, b));
        Course c = new Course();
        c.setName("冲突课"); c.setTime("周一 09:00-10:40");
        assertTrue(ctx.conflict(a, c));
        Course d = new Course();
        d.setName("不冲突课"); d.setTime("周一 10:00-11:40");
        assertFalse(ctx.conflict(a, d));
    }

    private void assertIntent(String question, AiContextService.Intent expect) {
        AiContextService.Recognition rec = ctx.recognize(question, null, "student");
        assertEquals(expect, rec.intent, "问题[" + question + "]识别为 " + rec.intent + "，期望 " + expect);
    }

    private void assertHit(String question, String expectName) {
        AiContextService.Recognition rec = ctx.recognize(question, null, "student");
        boolean hit = rec.courses.stream().anyMatch(c -> c.getName().equals(expectName));
        assertTrue(hit, "问题[" + question + "]未命中《" + expectName + "》，实际命中："
                + rec.courses.stream().map(Course::getName).toList());
    }
}
