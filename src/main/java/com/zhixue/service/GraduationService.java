package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.TrainingPlanCourse;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.GradeRecord;
import com.zhixue.entity.GradeRequirement;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.EnrollmentRepository;
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
    @Autowired private GradeLedgerService ledger;
    @Autowired private TrainingPlanService trainingPlanService;
    @Autowired private GradeRequirementRepository gradeReqRepo;
    @Autowired private UserRepository userRepo;

    /** 厦门华厦学院毕业学分只有「必修 / 选修」两类 */
    private static final String[] KEYS = CourseGroup.KEYS;

    /** 及格线：总评 ≥ 60 视为「已通过」，低于或未出成绩均不计入毕业学分 */
    private static final double PASS_LINE = 60.0;

    /** 课程类别 → 毕业学分大类（必修/选修）。统一使用 {@link CourseGroup#groupOf} */
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

    /**
     * 学期滚动：管理员推进学期后 offset>0，学生学期序随之前进（培养方案按学期滚动）。
     * 专升本两年制：最多 4 个学期，超过封顶为 4（已到毕业学期），不按普通本科推算到第 7/8 学期。
     */
    public static int currentSemesterNo(Integer grade, java.time.LocalDate today, int offset) {
        return currentSemesterNo(grade, null, today, offset);
    }

    /** 带学制的学期推算：major 含「专升本」按两年制（封顶 4 学期），否则普通本科四年制 */
    public static int currentSemesterNo(Integer grade, String major, java.time.LocalDate today, int offset) {
        if (grade == null || grade < 2000) return 1;
        int y = today.getYear(), m = today.getMonthValue();
        int sem;
        if (m >= 8) sem = (y - grade) * 2 + 1;
        else if (m == 1) sem = (y - grade) * 2 - 1;
        else sem = (y - grade) * 2;
        sem = Math.max(1, sem + Math.max(0, offset));
        boolean isZsb = major != null && major.contains("专升本");
        if (isZsb) sem = Math.min(4, sem);   // 专升本两年制：第 4 学期封顶
        return sem;
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

        // 毕业要求按学制两套（普通本科 / 专升本），不按年级分化：
        // 专升本学生（专业名带「专升本」）套「专升本」方案，其余学生套「普通本科」方案。
        User student = userRepo.findById(studentId).orElse(null);
        Integer stuGrade = student == null ? null : student.getGrade();
        String major = student == null ? null : student.getMajor();
        boolean isZsb = major != null && major.contains("专升本");
        List<GradeRequirement> allReqs = gradeReqRepo.findAllByOrderByGradeAsc();
        GradeRequirement gr;
        if (isZsb) {
            gr = allReqs.stream()
                    .filter(g -> g.getLabel() != null && g.getLabel().contains("专升本")).findFirst().orElse(null);
        } else {
            gr = allReqs.stream()
                    .filter(g -> g.getLabel() == null || !g.getLabel().contains("专升本")).findFirst().orElse(null);
        }
        int reqRequired = gr != null && gr.getGradReqRequired() != null ? gr.getGradReqRequired() : nz(cfg.getGradReqRequired());
        int reqPublicElec = gr != null && gr.getGradReqGeneral() != null && gr.getGradReqGeneral() > 0
                ? gr.getGradReqGeneral() : 6;   // 华厦硬指标：通识(公共)选修修满 6 学分（通识教育中心选课须知）
        int reqMajorElec = gr != null && gr.getGradReqElective() != null ? gr.getGradReqElective() : nz(cfg.getGradReqElective());

        Map<String, Integer> reqMap = new LinkedHashMap<>();
        reqMap.put("必修", reqRequired);
        reqMap.put("公共选修", reqPublicElec);
        reqMap.put("专业选修", reqMajorElec);

        // 专业选修分母 = 培养方案排给本专业的全部专业选修课总学分——
        // 「修满培养方案排的课 = 达标」，与必修「一门都不能缺」同一哲学。
        // 方案未配置时回退到配置值。
        try {
            int planZxCredits = 0;
            java.util.Set<String> zxCourseSeen = new java.util.HashSet<>();
            for (TrainingPlanCourse row : trainingPlanService.resolvePlanRows(major, stuGrade)) {
                Course c = courseService.findById(row.getCourseId());
                if (c == null) continue;
                if (c.getCategory() == null || !c.getCategory().contains("专业选修")) continue;
                if (!CourseGroup.countsForGraduation(c.getCourseCode())) continue;
                if (c.getName() == null || !zxCourseSeen.add(c.getName())) continue;   // 同名只算一门
                planZxCredits += nz(c.getCredit());
            }
            if (planZxCredits > 0) {
                reqMajorElec = planZxCredits;
                reqMap.put("专业选修", planZxCredits);
            }
        } catch (Exception ignored) { }

        // 「已通过」= 成绩流水有效记录通过（最新一次通过优先，挂科不被后续失败覆盖）
        // 与 GPA、专业排名共用 GradeLedgerService 的同一口径
        Map<Long, Course> approvedCourses = new LinkedHashMap<>();
        Map<Long, Double> passedScore = new HashMap<>();
        Map<Long, Double> failedScore = new HashMap<>();   // 挂科（有效记录 <60）：用于「重修中」判定
        for (Map.Entry<Long, GradeRecord> en : ledger.effectiveRecords(studentId).entrySet()) {
            GradeRecord g = en.getValue();
            if (g.getTotalScore() == null) continue;
            if (g.getTotalScore() >= PASS_LINE) passedScore.put(en.getKey(), g.getTotalScore());
            else failedScore.put(en.getKey(), g.getTotalScore());
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
        Set<String> earnedOnce = new HashSet<>();
        for (Course c : passedCourses.values()) {
            if (!CourseGroup.countsForGraduation(c.getCourseCode())) continue;   // 历史拓展课只还原课表密度，不计毕业学分
            if (c.getName() != null && !earnedOnce.add(c.getName())) continue;   // 同名课程只计一次学分
            String g = groupOf(c.getCategory());
            earned.put(g, earned.get(g) + nz(c.getCredit()));
        }

        // —— 教学班拆行后的归一化 ——
        // 一个课程目录会拆成多个教学班行（同名不同 id），毕业口径必须按「课程名」归并；
        // 按行号统计会得到「第 1 学期 614 门必修 / 1526 学分」这类荒谬数字。
        List<Course> allCourses = courseService.findAll();
        Map<Long, String> nameById = new HashMap<>();
        for (Course c : allCourses) {
            if (c.getId() != null && c.getName() != null) nameById.put(c.getId(), c.getName());
        }
        Set<String> passedNames = new HashSet<>();
        Map<String, Double> passedScoreByName = new HashMap<>();
        for (Map.Entry<Long, Double> en : passedScore.entrySet()) {
            String n = nameById.get(en.getKey());
            if (n != null) { passedNames.add(n); passedScoreByName.putIfAbsent(n, en.getValue()); }
        }
        Map<String, Double> failedScoreByName = new HashMap<>();
        for (Map.Entry<Long, Double> en : failedScore.entrySet()) {
            String n = nameById.get(en.getKey());
            if (n != null && !passedNames.contains(n)) failedScoreByName.putIfAbsent(n, en.getValue());
        }

        // 必修逐门校验：教学计划中每一门必修课都必须「已录取且总评 ≥ 60」
        // retaking：本轮已有该课报名（必修课每轮自动重排，挂科即视为重修中）——前端据此显示「重修中」
        int curRound = enrollmentService.getSystemConfig().getCurrentRound();
        Set<Long> inProgress = new HashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentIdAndRound(studentId, curRound)) {
            if ("approved".equals(e.getStatus()) || "pending".equals(e.getStatus())) inProgress.add(e.getCourseId());
        }
        Set<String> inProgressNames = new HashSet<>();
        for (Long cid : inProgress) {
            String n = nameById.get(cid);
            if (n != null) inProgressNames.add(n);
        }
        List<String> missingRequired = new ArrayList<>();
        List<Map<String, Object>> missingDetails = new ArrayList<>();
        Set<String> missingSeen = new HashSet<>();
        // 必修缺课口径 v2:分母与缺课清单来自培养方案行(教学计划),
        // 与选课/课表/成绩的方案学期完全一致;方案外课程不计入毕业缺口
        for (TrainingPlanCourse row : trainingPlanService.resolvePlanRows(major, stuGrade)) {
            Course c = courseService.findById(row.getCourseId());
            if (c == null) continue;   // 方案引用的课程已删除
            if (c.getCategory() == null || !c.getCategory().contains("必修")) continue;
            if (c.getName() == null || !missingSeen.add(c.getName())) continue;   // 同名只算一门
            if (!passedScore.containsKey(c.getId())) {
                missingRequired.add(c.getName());
                Map<String, Object> md = new LinkedHashMap<>();
                md.put("name", c.getName());
                md.put("retaking", inProgressNames.contains(c.getName()));
                missingDetails.add(md);
            }
        }

        // 必修课按培养方案学期分组进度（只含必修）：选修从入学到毕业总量达标即可，
        // 必修则是每学期不同的课——任何一学期的必修没过都无法毕业，按学期展示让缺口一目了然。
        int curSem = currentSemesterNo(stuGrade, major, java.time.LocalDate.now(),
                cfg.getSemesterOffset() == null ? 0 : cfg.getSemesterOffset());
        Map<Integer, List<Course>> bySem = new TreeMap<>();
        for (TrainingPlanCourse row : trainingPlanService.resolvePlanRows(major, stuGrade)) {
            Course c = courseService.findById(row.getCourseId());
            if (c == null) continue;
            if (c.getCategory() == null || !c.getCategory().contains("必修")) continue;
            int sno = row.getSemesterNo() != null && row.getSemesterNo() > 0 ? row.getSemesterNo() : 0;
            bySem.computeIfAbsent(sno, k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> semesters = new ArrayList<>();
        for (Map.Entry<Integer, List<Course>> en : bySem.entrySet()) {
            int sno = en.getKey();
            List<Map<String, Object>> cs = new ArrayList<>();
            int total = 0, passedCnt = 0, credits = 0, semEarned = 0;
            for (Course c : en.getValue()) {
                String cname = c.getName();
                boolean p = passedNames.contains(cname);
                boolean failed = failedScoreByName.containsKey(cname);
                // 无成绩记录就是「暂无成绩」：不臆断为通过/挂科，也不臆断为待出成绩或需重修。
                // 成绩由教师录入后才决定状态；这里只如实反映「有无成绩」，避免演示数据被误读成大规模挂科。
                boolean noScore = !p && !failed;
                total++;
                credits += nz(c.getCredit());
                if (p) { passedCnt++; semEarned += nz(c.getCredit()); }
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("name", cname);
                cm.put("credit", nz(c.getCredit()));
                cm.put("passed", p);
                cm.put("failed", failed);
                cm.put("noScore", noScore);
                cm.put("score", passedScoreByName.containsKey(cname) ? passedScoreByName.get(cname) : failedScoreByName.get(cname));
                // 重修中：必须确有挂科成绩（<60）且本轮在修；无成绩不算重修
                cm.put("retaking", failed && inProgressNames.contains(cname));
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
            // 学生看不到以后的课表（真实教务口径）：未来学期只公示规划学分，
            // 具体课程排课要等学期推进进入该学期后才对学生可见。
            sm.put("courses", sno > curSem ? new ArrayList<>() : cs);
            sm.put("planned", sno > curSem);
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
        // 必修的达标规则是「一门都不能缺」，分母必须是培养方案里必修课的总学分（约 105 学分），
        // 而不是可配置的毕业门槛（默认 18，那是给演示/弹性学分留的值）。
        // 否则会出现「18/18 差 0 学分」却同时列着 19 门未通过的矛盾展示。
        int planRequiredCredits = 0;
        for (List<Course> list : bySem.values()) {
            for (Course c : list) planRequiredCredits += nz(c.getCredit());
        }
        if (planRequiredCredits > reqRequired) reqRequired = planRequiredCredits;
        reqMap.put("必修", reqRequired);
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
        // 毕业按「必修 + 公共选修 + 专业选修」三类学分分别达标，不再单列实践与最少课程数
        boolean graduated = allDone && totalEarned >= totalReq;

        List<Map<String, Object>> recommend = recommendCourses(approvedIds, earned, reqMap);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("required", totalReq);
        total.put("earned", totalEarned);
        total.put("gap", Math.max(0, totalReq - totalEarned));
        total.put("percent", totalReq <= 0 ? 0 : (int) Math.round(totalEarned * 100.0 / totalReq));
        result.put("total", total);
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
        // 毕业要求按学制两套，不按年级分化：标签只标学制（普通本科 / 专升本）
        result.put("requirementLabel", isZsb ? "专升本" : "普通本科");
        result.put("isZsb", isZsb);
        return result;
    }

    /**
     * 推荐可修课程：只推荐公共选修（通识选修）。
     * 它是学生唯一自主选择的类别；必修与专业选修由教务按培养方案在开学前排入课表，
     * 学生提前无法预知后续学期上什么课，因此不做预告式推荐。
     */
    private List<Map<String, Object>> recommendCourses(Set<Long> approvedIds,
                                                       Map<String, Integer> earned,
                                                       Map<String, Integer> reqMap) {
        List<Map<String, Object>> out = new ArrayList<>();
        int gxGap = reqMap.getOrDefault("公共选修", 0) - earned.getOrDefault("公共选修", 0);
        if (gxGap <= 0) return out;   // 通识选修已修满：不再推荐
        for (Course c : courseService.findAll()) {
            if (approvedIds.contains(c.getId())) continue;
            if (c.getCategory() == null || !c.getCategory().contains("公共选修")) continue;
            if (!CourseGroup.countsForGraduation(c.getCourseCode())) continue;   // 历史拓展课不推荐
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("category", c.getCategory());
            m.put("credit", nz(c.getCredit()));
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("location", c.getLocation());
            m.put("intro", c.getIntro());
            m.put("group", "公共选修");
            m.put("groupGap", gxGap);
            out.add(m);
        }
        out.sort((a, b) -> Integer.compare((Integer) b.get("credit"), (Integer) a.get("credit")));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    /**
     * 必修课按专业归属过滤：target_major 非空且学生专业非空时，学生专业须在目标专业清单内
     * （| 分隔，兼容全/半角括号后缀如「数字媒体艺术（闽台合作）」）。未标注专业 = 全院/全校通用。
     * 此前只按学院过滤，同学院其他专业（音乐学、商务英语等）的必修被误算成本专业的毕业缺口。
     */
    private boolean majorMatches(Course c, User student) {
        String targets = c.getTargetMajor();
        String major = student == null ? null : student.getMajor();
        if (targets == null || targets.isBlank() || major == null || major.isBlank()) return true;
        String my = major.replaceAll("[（(].*$", "").trim();
        for (String x : targets.split("\\|")) {
            if (x.replaceAll("[（(].*$", "").trim().equals(my)) return true;
        }
        return false;
    }
}
