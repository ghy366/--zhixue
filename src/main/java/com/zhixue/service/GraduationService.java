package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.GradeRequirement;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.GradeRepository;
import com.zhixue.repository.GradeRequirementRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.util.CourseGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraduationService {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseService courseService;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private GradeRepository gradeRepo;
    @Autowired private GradeRequirementRepository gradeReqRepo;
    @Autowired private UserRepository userRepo;

    private static final String[] KEYS = {"必修", "选修", "通识", "实践"};

    /** 及格线：总评 ≥ 60 视为「已通过」，低于或未出成绩均不计入毕业学分 */
    private static final double PASS_LINE = 60.0;

    /** 课程类别 → 毕业学分大类（必修/选修/通识/实践）。统一使用 {@link CourseGroup#groupOf} */
    public String groupOf(String category) {
        return CourseGroup.groupOf(category);
    }

    /**
     * 学生当前学期序（1 起，参照真实高校学籍学期推算）：入学年份 9 月为第 1 学期。
     * 9~12 月 = (year-grade)*2+1；1 月 = 上一年入学的学期末；2~7 月 = (year-grade)*2。
     * 例：2023 级学生在 2026 年 10 月处于第 7 学期（大三上 → 大四上之间按 2 学期/学年推进）。
     */
    public static int currentSemesterNo(Integer grade, java.time.LocalDate today) {
        return currentSemesterNo(grade, today, 0);
    }

    /** 学期滚动：管理员推进学期后 offset>0，学生学期序随之前进（培养方案按学期滚动） */
    public static int currentSemesterNo(Integer grade, java.time.LocalDate today, int offset) {
        if (grade == null || grade < 2000) return 1;
        int y = today.getYear(), m = today.getMonthValue();
        int sem;
        if (m >= 8) sem = (y - grade) * 2 + 1;
        else if (m == 1) sem = (y - grade) * 2 - 1;
        else sem = (y - grade) * 2;
        return Math.max(1, sem + Math.max(0, offset));
    }

    /** 第 sem 学期对应的学年学期名（第 7 学期 → 2026-2027学年第一学期） */
    public static String semesterLabel(Integer grade, int sem) {
        if (grade == null || grade < 2000) return "第 " + sem + " 学期";
        int startYear = grade + (sem - 1) / 2;
        return startYear + "-" + (startYear + 1) + "学年" + (sem % 2 == 1 ? "第一" : "第二") + "学期";
    }

    /** 毕业进度汇总：各类别已获/差额 + 推荐可修课程 */
    public Map<String, Object> summary(Long studentId) {
        // 必修课由系统自动排课：先补齐，保证必修学分直接计入毕业进度
        try { enrollmentService.ensureRequiredEnrollments(studentId); } catch (Exception ignored) {}
        SystemConfig cfg = enrollmentService.getSystemConfig();

        // 按年级差异化：学生所在年级有专属培养方案（如专升本）则优先适用，否则用全校默认要求
        User student = userRepo.findById(studentId).orElse(null);
        Integer stuGrade = student == null ? null : student.getGrade();
        GradeRequirement gr = null;
        if (stuGrade != null) {
            // 专业优先：同年级先找「年级+专业」精确匹配的培养方案，再退回该年级通用方案
            List<GradeRequirement> gradeReqs = gradeReqRepo.findByGrade(stuGrade);
            String major = student != null ? student.getMajor() : null;
            gr = gradeReqs.stream().filter(g -> major != null && major.equals(g.getMajor())).findFirst()
                    .orElseGet(() -> gradeReqs.stream()
                            .filter(g -> g.getMajor() == null || g.getMajor().isBlank())
                            .findFirst().orElse(null));
        }
        int reqRequired = gr != null && gr.getGradReqRequired() != null ? gr.getGradReqRequired() : nz(cfg.getGradReqRequired());
        int reqElective = gr != null && gr.getGradReqElective() != null ? gr.getGradReqElective() : nz(cfg.getGradReqElective());
        int reqGeneral  = gr != null && gr.getGradReqGeneral()  != null ? gr.getGradReqGeneral()  : nz(cfg.getGradReqGeneral());
        int reqPractice = gr != null && gr.getGradReqPractice() != null ? gr.getGradReqPractice() : nz(cfg.getGradReqPractice());
        int reqCourses  = gr != null && gr.getGradReqCourses()  != null ? gr.getGradReqCourses()  : nz(cfg.getGradReqCourses());

        Map<String, Integer> reqMap = new LinkedHashMap<>();
        reqMap.put("必修", reqRequired);
        reqMap.put("选修", reqElective);
        reqMap.put("通识", reqGeneral);
        reqMap.put("实践", reqPractice);

        // 「已通过」= 已录取 且 总评 ≥ 60（未出成绩按未通过计，不参与学分汇总）
        Map<Long, Course> approvedCourses = new LinkedHashMap<>();
        Map<Long, Double> passedScore = new HashMap<>();
        Map<Long, Double> failedScore = new HashMap<>();   // 挂科（有成绩且 <60）：用于「重修中」判定
        for (Grade g : gradeRepo.findByStudentId(studentId)) {
            if (g.getTotalScore() != null) {
                if (g.getTotalScore() >= PASS_LINE) passedScore.put(g.getCourseId(), g.getTotalScore());
                else failedScore.put(g.getCourseId(), g.getTotalScore());
            }
        }
        // 同一门课程可能在多轮次都有记录，按课程去重，避免学分重复累加
        Set<Long> approvedIds = new HashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;
            approvedCourses.putIfAbsent(c.getId(), c);
        }
        Map<Long, Course> passedCourses = new LinkedHashMap<>();
        for (Map.Entry<Long, Course> en : approvedCourses.entrySet()) {
            if (passedScore.containsKey(en.getKey())) passedCourses.put(en.getKey(), en.getValue());
        }
        for (Course c : passedCourses.values()) approvedIds.add(c.getId());

        Map<String, Integer> earned = new LinkedHashMap<>();
        for (String k : KEYS) earned.put(k, 0);
        for (Course c : passedCourses.values()) {
            String g = groupOf(c.getCategory());
            earned.put(g, earned.get(g) + nz(c.getCredit()));
        }

        // 必修逐门校验：教学计划中每一门必修课都必须「已录取且总评 ≥ 60」
        // retaking：本轮已有该课报名（必修课每轮自动重排，挂科即视为重修中）——前端据此显示「重修中」
        int curRound = enrollmentService.getSystemConfig().getCurrentRound();
        Set<Long> inProgress = new HashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentIdAndRound(studentId, curRound)) {
            if ("approved".equals(e.getStatus()) || "pending".equals(e.getStatus())) inProgress.add(e.getCourseId());
        }
        List<String> missingRequired = new ArrayList<>();
        List<Map<String, Object>> missingDetails = new ArrayList<>();
        for (Course c : courseService.findAll()) {
            if (c.getCategory() == null || !c.getCategory().contains("必修")) continue;
            if (!passedCourses.containsKey(c.getId())) {
                missingRequired.add(c.getName());
                Map<String, Object> md = new LinkedHashMap<>();
                md.put("name", c.getName());
                md.put("retaking", inProgress.contains(c.getId()));
                missingDetails.add(md);
            }
        }

        // 必修课按培养方案学期分组进度（只含必修）：选修从入学到毕业总量达标即可，
        // 必修则是每学期不同的课——任何一学期的必修没过都无法毕业，按学期展示让缺口一目了然。
        int curSem = currentSemesterNo(stuGrade, java.time.LocalDate.now(),
                cfg.getSemesterOffset() == null ? 0 : cfg.getSemesterOffset());
        Map<Integer, List<Course>> bySem = new TreeMap<>();
        for (Course c : courseService.findAll()) {
            if (c.getCategory() == null || !c.getCategory().contains("必修")) continue;
            int sno = c.getSemesterNo() != null && c.getSemesterNo() > 0 ? c.getSemesterNo() : 0;
            bySem.computeIfAbsent(sno, k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> semesters = new ArrayList<>();
        for (Map.Entry<Integer, List<Course>> en : bySem.entrySet()) {
            int sno = en.getKey();
            List<Map<String, Object>> cs = new ArrayList<>();
            int total = 0, passedCnt = 0, credits = 0, semEarned = 0;
            for (Course c : en.getValue()) {
                boolean p = passedCourses.containsKey(c.getId());
                boolean failed = failedScore.containsKey(c.getId());
                boolean pendingScore = !p && !failed;   // 已到学期但无成绩：已修待出分（或旧数据缺失）
                total++;
                credits += nz(c.getCredit());
                if (p) { passedCnt++; semEarned += nz(c.getCredit()); }
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("name", c.getName());
                cm.put("credit", nz(c.getCredit()));
                cm.put("passed", p);
                cm.put("failed", failed);
                cm.put("pendingScore", pendingScore);
                cm.put("score", passedScore.containsKey(c.getId()) ? passedScore.get(c.getId()) : failedScore.get(c.getId()));
                // 重修中 = 确实挂科（有成绩且 <60）且本轮在修；未出成绩的课是「已修待出分」，不标
                cm.put("retaking", failed && inProgress.contains(c.getId()));
                cs.add(cm);
            }
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("semesterNo", sno);
            sm.put("label", sno == 0 ? "未排期必修" : "第 " + sno + " 学期");
            sm.put("schoolYear", sno == 0 ? null : semesterLabel(stuGrade, sno));
            sm.put("current", sno == curSem);
            sm.put("future", sno > curSem);
            sm.put("total", total);
            sm.put("passed", passedCnt);
            sm.put("credits", credits);
            sm.put("earned", semEarned);
            sm.put("done", passedCnt == total);
            sm.put("courses", cs);
            semesters.add(sm);
        }
        // 当前学期没有排必修课（如大四毕设环节）也补一行，给学生"你在这里"的学期定位
        if (stuGrade != null && semesters.stream().noneMatch(s -> (Integer) s.get("semesterNo") == curSem)) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("semesterNo", curSem);
            sm.put("label", "第 " + curSem + " 学期");
            sm.put("schoolYear", semesterLabel(stuGrade, curSem));
            sm.put("current", true);
            sm.put("future", false);
            sm.put("total", 0);
            sm.put("passed", 0);
            sm.put("credits", 0);
            sm.put("earned", 0);
            sm.put("done", true);
            sm.put("courses", new ArrayList<>());
            semesters.add(sm);
            semesters.sort(Comparator.comparingInt(s -> (Integer) s.get("semesterNo")));
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        int totalReq = 0, totalEarned = 0;
        boolean allDone = true;
        for (String k : KEYS) {
            int req = reqMap.get(k), got = earned.get(k);
            // 单个模块超出要求的学分不计入总进度：一类修满不能抵扣其他模块的缺口
            int counted = Math.min(got, req);
            totalReq += req; totalEarned += counted;
            boolean done = got >= req && ("必修".equals(k) ? missingRequired.isEmpty() : true);
            if (!done) allDone = false;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k);
            m.put("required", req);
            m.put("earned", got);
            m.put("gap", Math.max(0, req - got));
            m.put("percent", req <= 0 ? 0 : (int) Math.round(counted * 100.0 / req));
            m.put("done", done);
            if ("必修".equals(k)) m.put("missing", missingRequired);
            categories.add(m);
        }
        // 学分与课程数双达标：真实高校「修满规定学分 + 修满课程门数」模式
        int coursesEarned = passedCourses.size();
        Map<String, Object> courses = new LinkedHashMap<>();
        courses.put("required", reqCourses);
        courses.put("earned", coursesEarned);
        courses.put("gap", Math.max(0, reqCourses - coursesEarned));
        courses.put("percent", reqCourses <= 0 ? 0 : (int) Math.round(Math.min(coursesEarned, reqCourses) * 100.0 / reqCourses));
        courses.put("done", coursesEarned >= reqCourses);

        boolean graduated = allDone && totalEarned >= totalReq && coursesEarned >= reqCourses;

        List<Map<String, Object>> recommend = recommendCourses(approvedIds, earned, reqMap);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("required", totalReq);
        total.put("earned", totalEarned);
        total.put("gap", Math.max(0, totalReq - totalEarned));
        total.put("percent", totalReq <= 0 ? 0 : (int) Math.round(totalEarned * 100.0 / totalReq));
        result.put("total", total);
        result.put("courses", courses);
        result.put("graduated", graduated);
        result.put("missingRequired", missingRequired);
        result.put("missingDetails", missingDetails);
        result.put("semesters", semesters);
        result.put("currentSemesterNo", curSem);
        result.put("currentSemesterLabel", stuGrade != null
                ? "第 " + curSem + " 学期 · " + semesterLabel(stuGrade, curSem)
                : null);
        result.put("recommend", recommend);
        result.put("requirementSource", gr != null ? "grade" : "default");
        result.put("requirementLabel", gr != null
                ? (stuGrade + " 级"
                    + (gr.getMajor() != null && !gr.getMajor().isBlank() ? " · " + gr.getMajor() : "")
                    + " · " + (gr.getLabel() == null || gr.getLabel().isBlank() ? "定制培养方案" : gr.getLabel()))
                : "全校默认要求");
        return result;
    }

    /** 推荐可修课程：只推荐仍有差额的模块中的、学生尚未录取的课程 */
    private List<Map<String, Object>> recommendCourses(Set<Long> approvedIds,
                                                       Map<String, Integer> earned,
                                                       Map<String, Integer> reqMap) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Course c : courseService.findAll()) {
            if (approvedIds.contains(c.getId())) continue;
            // 必修课只能由教务系统自动安排，不进入推荐（学生无法自选必修）
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            String g = groupOf(c.getCategory());
            int gap = reqMap.getOrDefault(g, 0) - earned.getOrDefault(g, 0);
            if (gap <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("category", c.getCategory());
            m.put("credit", nz(c.getCredit()));
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("location", c.getLocation());
            m.put("intro", c.getIntro());
            m.put("group", g);
            m.put("groupGap", gap);
            out.add(m);
        }
        out.sort((a, b) -> {
            int d = Integer.compare((Integer) b.get("groupGap"), (Integer) a.get("groupGap"));
            if (d != 0) return d;
            return Integer.compare((Integer) b.get("credit"), (Integer) a.get("credit"));
        });
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }
}
