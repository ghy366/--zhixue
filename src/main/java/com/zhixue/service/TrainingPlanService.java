package com.zhixue.service;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 培养方案服务：按「专业 + 入学年级 + 学期」维护应修课程清单，由教务系统据此批量排定学生必修课。
 * 对齐厦门华厦学院真实做法——必修课按培养方案由教务系统排定，学生不在选课中选、也删不掉。
 */
@Service
public class TrainingPlanService {

    @Autowired private TrainingPlanCourseRepository planRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private EnrollmentService enrollmentService;

    /** 去掉「(专升本)」等学制后缀 */
    private String normMajor(String m) {
        if (m == null || m.isBlank()) return "";
        int i = m.indexOf('(');
        if (i < 0) i = m.indexOf('（');
        return (i > 0 ? m.substring(0, i) : m).trim();
    }

    /** 全量方案（管理页用），附带课程名/学分，按专业+年级+学期排序 */
    public List<Map<String, Object>> listAll() {
        Map<Long, Course> cm = courseRepo.findAll().stream().collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
        return planRepo.findAllByOrderByMajorAscGradeAscSemesterNoAscIdAsc().stream()
                .map(t -> toMap(t, cm.get(t.getCourseId())))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> listByMajor(String major) {
        Map<Long, Course> cm = courseRepo.findAll().stream().collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
        return planRepo.findByMajorOrderBySemesterNoAscIdAsc(major).stream()
                .map(t -> toMap(t, cm.get(t.getCourseId())))
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(TrainingPlanCourse t, Course c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("major", t.getMajor());
        m.put("grade", t.getGrade());
        m.put("semesterNo", t.getSemesterNo());
        m.put("courseId", t.getCourseId());
        m.put("courseName", c == null ? "（课程已删除）" : c.getName());
        m.put("courseCode", c == null ? "" : c.getCourseCode());
        m.put("category", t.getCategory() != null ? t.getCategory() : (c == null ? "" : c.getCategory()));
        m.put("credit", t.getCredit() != null ? t.getCredit() : (c == null ? null : c.getCredit()));
        m.put("enabled", t.getEnabled() != false);
        m.put("note", t.getNote());
        return m;
    }

    /** 方案统计：专业数、记录数、启用数、涉及课程数、覆盖学生数 */
    public Map<String, Object> stats() {
        List<TrainingPlanCourse> all = planRepo.findAll();
        Set<String> majors = all.stream().map(TrainingPlanCourse::getMajor).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> courseIds = all.stream().filter(t -> t.getEnabled() != false).map(TrainingPlanCourse::getCourseId).collect(Collectors.toSet());
        long students = userRepo.findAll().stream()
                .filter(u -> "student".equals(u.getRole()))
                .filter(u -> majors.contains(normMajor(u.getMajor())))
                .count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("majors", majors.size());
        m.put("total", all.size());
        m.put("enabled", all.stream().filter(t -> t.getEnabled() != false).count());
        m.put("courses", courseIds.size());
        m.put("students", students);
        return m;
    }

    /** 预览批量排定影响面：按专业+年级，列出学生数与应修课程门数（不写库） */
    public List<Map<String, Object>> preview() {
        Map<Long, Course> cm = courseRepo.findAll().stream().collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
        List<User> students = userRepo.findAll().stream().filter(u -> "student".equals(u.getRole())).toList();
        Map<String, List<User>> groups = students.stream().collect(Collectors.groupingBy(
                u -> normMajor(u.getMajor()) + "|" + (u.getGrade() == null ? 0 : u.getGrade()), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<User>> e : groups.entrySet()) {
            String[] p = e.getKey().split("\\|");
            String major = p[0];
            int grade = Integer.parseInt(p[1]);
            List<Course> plan = resolveCourses(major, grade);
            if (plan.isEmpty()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("major", major);
            row.put("grade", grade == 0 ? null : grade);
            row.put("students", e.getValue().size());
            row.put("courses", plan.size());
            row.put("credits", plan.stream().mapToInt(c -> c.getCredit() == null ? 0 : c.getCredit()).sum());
            out.add(row);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> m.get("major").toString())
                .thenComparing(m -> (Integer) m.get("grade"), Comparator.nullsFirst(Integer::compareTo)));
        return out;
    }

    /**
     * 解析某专业+年级的应修课程：年级专属方案优先，通用方案(grade=null)兜底。
     * 若该专业完全没有方案记录，回退到 EnrollmentService 旧的按课程属性推导（迁移期兼容）。
     */
    public List<Course> resolveCourses(String major, Integer grade) {
        // 原始专业名（含专升本）优先，回退归一化本科专业
        List<TrainingPlanCourse> tpc = planRepo.findApplicable(major, grade);
        if (tpc.isEmpty()) {
            String nm = normMajor(major);
            tpc = planRepo.findApplicable(nm, grade);
            if (tpc.isEmpty()) return enrollmentService.requiredCoursesForCollege(null, nm);
        }

        // 年级专属优先：同一 courseId 若同时出现在专属与通用，取专属（学期以专属为准）
        Map<Long, TrainingPlanCourse> best = new LinkedHashMap<>();
        for (TrainingPlanCourse t : tpc) {
            TrainingPlanCourse old = best.get(t.getCourseId());
            if (old == null || (t.getGrade() != null && old.getGrade() == null)) best.put(t.getCourseId(), t);
        }
        Map<Long, Course> cm = courseRepo.findAll().stream().collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
        List<Course> out = new ArrayList<>();
        for (TrainingPlanCourse t : best.values()) {
            Course c = cm.get(t.getCourseId());
            if (c != null) out.add(c);
        }
        out.sort(Comparator.comparing(Course::getId));
        return out;
    }

    /**
     * 解析方案行(含计划学期):年级专属优先,通用兜底,同一课程取专属行;
     * 无方案行的专业按课程属性推导伪行(学期取课程自身 semester_no)。
     * 供毕业进度按「教学计划」口径计算必修分母/缺课清单 —— 与选课/课表的方案学期完全一致。
     */
    public List<TrainingPlanCourse> resolvePlanRows(String major, Integer grade) {
        List<TrainingPlanCourse> tpc = planRepo.findApplicable(major, grade);
        if (tpc.isEmpty()) {
            String nm = normMajor(major);
            if (nm != null && !nm.equals(major)) tpc = planRepo.findApplicable(nm, grade);
        }
        if (!tpc.isEmpty()) {
            Map<Long, TrainingPlanCourse> best = new LinkedHashMap<>();
            for (TrainingPlanCourse t : tpc) {
                TrainingPlanCourse old = best.get(t.getCourseId());
                if (old == null || (t.getGrade() != null && old.getGrade() == null)) best.put(t.getCourseId(), t);
            }
            return new ArrayList<>(best.values());
        }
        List<TrainingPlanCourse> out = new ArrayList<>();
        for (Course c : enrollmentService.requiredCoursesForCollege(null, normMajor(major))) {
            TrainingPlanCourse t = new TrainingPlanCourse();
            t.setMajor(major != null ? major : "");
            t.setSemesterNo(c.getSemesterNo() == null ? 0 : c.getSemesterNo());
            t.setCourseId(c.getId()); t.setCategory(c.getCategory()); t.setCredit(c.getCredit());
            out.add(t);
        }
        return out;
    }

    /** 按培养方案为单个学生排定必修课（委托 EnrollmentService 的幂等写入），返回新增条数 */
    @Transactional
    public int ensureForStudent(Long studentId) {
        return enrollmentService.ensureRequiredEnrollments(studentId);
    }

    /**
     * 教务端一键批量排定：一次性加载学生/课程/报名/成绩/方案到内存，批量计算后一次写库。
     * 避免逐个学生调用 ensureRequiredEnrollments 造成的 N×(查全表课程+逐门查成绩/报名) 性能灾难。
     */
    @Transactional
    public Map<String, Object> batchEnsure() {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        int offset = enrollmentService.getSystemConfig().getSemesterOffset() == null
                ? 0 : enrollmentService.getSystemConfig().getSemesterOffset();

        List<User> students = userRepo.findAll().stream().filter(u -> "student".equals(u.getRole())).toList();
        Map<Long, Course> courses = courseRepo.findAll().stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
        Map<Long, Map<Long, Enrollment>> existing = new HashMap<>();
        for (Enrollment e : enrollmentService.allEnrollments()) {
            existing.computeIfAbsent(e.getStudentId(), k -> new HashMap<>()).put(e.getCourseId(), e);
        }
        Map<Long, Map<Long, com.zhixue.entity.GradeRecord>> passed = new HashMap<>();
        for (Map.Entry<Long, Map<Long, com.zhixue.entity.GradeRecord>> e : enrollmentService.allEffectiveRecords().entrySet()) {
            Map<Long, com.zhixue.entity.GradeRecord> ok = e.getValue().entrySet().stream()
                    .filter(x -> x.getValue().getTotalScore() != null && x.getValue().getTotalScore() >= 60)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            passed.put(e.getKey(), ok);
        }
        // 方案：(专业原名含专升本, 年级) -> 应修 (courseId -> semester)，0 = 用学生当前学期
        // 不再归一化专业名——专升本方案(如 环境科学与工程(专升本))与本科方案是两套独立配置
        Map<String, Map<Long, Integer>> planByMajorGrade = new HashMap<>();
        for (TrainingPlanCourse t : planRepo.findAll()) {
            if (t.getEnabled() == false) continue;
            String key = t.getMajor() + "|" + (t.getGrade() == null ? 0 : t.getGrade());
            planByMajorGrade.computeIfAbsent(key, k -> new HashMap<>())
                    .put(t.getCourseId(), t.getSemesterNo() == null ? 0 : t.getSemesterNo());
        }

        List<Enrollment> toAdd = new ArrayList<>();
        int ok = 0;
        for (User stu : students) {
            String raw = stu.getMajor();
            String nm = normMajor(raw);
            int grade = stu.getGrade() == null ? 0 : stu.getGrade();
            // 专升本/原始专业方案优先，其次年级专属本科方案，最后通用本科方案
            Map<Long, Integer> specific = planByMajorGrade.get(raw + "|" + grade);
            if (specific == null) specific = planByMajorGrade.get(raw + "|0");
            if (specific == null) specific = planByMajorGrade.get(nm + "|" + grade);
            Map<Long, Integer> generic = planByMajorGrade.get(nm + "|0");
            Map<Long, Integer> plan = new LinkedHashMap<>();
            if (generic != null) plan.putAll(generic);
            if (specific != null) plan.putAll(specific);   // 年级专属覆盖通用
            if (plan.isEmpty()) {
                // 该专业未配方案：回退按课程属性推导
                for (Course c : enrollmentService.requiredCoursesForCollege(stu.getCollege(), stu.getMajor())) {
                    plan.put(c.getId(), c.getSemesterNo() == null ? 0 : c.getSemesterNo());
                }
            }
            Map<Long, Enrollment> enrMap = existing.getOrDefault(stu.getId(), Map.of());
            Map<Long, com.zhixue.entity.GradeRecord> passedMap = passed.getOrDefault(stu.getId(), Map.of());
            int curSem = com.zhixue.service.GraduationService.currentSemesterNo(
                    stu.getGrade(), stu.getMajor(), java.time.LocalDate.now(), offset);
            for (Map.Entry<Long, Integer> pe : plan.entrySet()) {
                Course c = courses.get(pe.getKey());
                if (c == null) continue;
                if (passedMap.containsKey(c.getId())) continue;   // 已通过不重排
                int sem = pe.getValue() > 0 ? pe.getValue() : curSem;
                Enrollment e = enrMap.get(c.getId());
                if (e == null) {
                    Enrollment ne = new Enrollment();
                    ne.setStudentId(stu.getId()); ne.setCourseId(c.getId()); ne.setRound(round);
                    ne.setPoints(0); ne.setStatus("approved"); ne.setSemesterNo(sem);
                    toAdd.add(ne);
                } else if (e.getSemesterNo() == null || e.getSemesterNo() < 1) {
                    e.setSemesterNo(sem);
                    enrollmentService.saveEnrollment(e);
                }
            }
            ok++;
        }
        if (!toAdd.isEmpty()) enrollmentService.saveEnrollments(toAdd);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("students", students.size());
        m.put("processed", ok);
        m.put("added", toAdd.size());
        m.put("failed", 0);
        m.put("errors", List.of());
        return m;
    }

    /** 新增方案条目 */
    @Transactional
    public Map<String, Object> add(Map<String, Object> body) {
        String major = str(body.get("major"));
        Integer grade = intOrNull(body.get("grade"));
        Integer sem = intOrNull(body.get("semesterNo"));
        Long cid = longOrNull(body.get("courseId"));
        if (major.isEmpty() || sem == null || cid == null) return fail("专业、学期、课程为必填");
        Course course = courseRepo.findById(cid).orElse(null);
        if (course == null) return fail("课程不存在");
        if (!com.zhixue.util.CourseGroup.isRequired(course.getCategory()))
            return fail("培养方案只容纳必修/专业选修类课程，选修课请走学生自由选课");
        if (planRepo.findByMajorAndGradeOrderBySemesterNoAscIdAsc(major, grade).stream()
                .anyMatch(t -> Objects.equals(t.getSemesterNo(), sem) && Objects.equals(t.getCourseId(), cid))) {
            return fail("该方案下已存在此课程（专业+年级+学期+课程唯一）");
        }
        TrainingPlanCourse t = new TrainingPlanCourse();
        t.setMajor(major); t.setGrade(grade); t.setSemesterNo(sem); t.setCourseId(cid);
        t.setCategory(str(body.get("category"))); t.setCredit(intOrNull(body.get("credit")));
        t.setNote(str(body.get("note")));
        Object en = body.get("enabled"); t.setEnabled(en == null || Boolean.TRUE.equals(en) || "1".equals(String.valueOf(en)));
        planRepo.save(t);
        Map<String, Object> r = ok("已加入培养方案"); r.put("id", t.getId()); return r;
    }

    /** 修改方案条目 */
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        TrainingPlanCourse t = planRepo.findById(id).orElse(null);
        if (t == null) return fail("方案条目不存在");
        if (body.containsKey("semesterNo")) { Integer v = intOrNull(body.get("semesterNo")); if (v != null) t.setSemesterNo(v); }
        if (body.containsKey("category")) t.setCategory(str(body.get("category")));
        if (body.containsKey("credit")) t.setCredit(intOrNull(body.get("credit")));
        if (body.containsKey("note")) t.setNote(str(body.get("note")));
        if (body.containsKey("enabled")) {
            Object en = body.get("enabled"); t.setEnabled(Boolean.TRUE.equals(en) || "1".equals(String.valueOf(en)) || "true".equalsIgnoreCase(String.valueOf(en)));
        }
        planRepo.save(t);
        return ok("已保存");
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        if (!planRepo.existsById(id)) return fail("方案条目不存在");
        planRepo.deleteById(id);
        return ok("已从培养方案移除");
    }

    /** 整段替换某专业+年级的方案（管理页批量保存用） */
    @Transactional
    public Map<String, Object> replace(String major, Integer grade, List<Map<String, Object>> items) {
        planRepo.deleteByMajorAndGrade(major, grade);
        int n = 0;
        for (Map<String, Object> b : items) {
            Integer sem = intOrNull(b.get("semesterNo"));
            Long cid = longOrNull(b.get("courseId"));
            if (sem == null || cid == null) continue;
            TrainingPlanCourse t = new TrainingPlanCourse();
            t.setMajor(major); t.setGrade(grade); t.setSemesterNo(sem); t.setCourseId(cid);
            t.setCategory(str(b.get("category"))); t.setCredit(intOrNull(b.get("credit")));
            t.setNote(str(b.get("note")));
            Object en = b.get("enabled"); t.setEnabled(en == null || Boolean.TRUE.equals(en) || "1".equals(String.valueOf(en)));
            planRepo.save(t); n++;
        }
        Map<String, Object> r = ok("培养方案已保存"); r.put("saved", n); return r;
    }

    private String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private Integer intOrNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try { return Integer.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private Long longOrNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }
    private Map<String, Object> ok(String msg) { Map<String, Object> m = new LinkedHashMap<>(); m.put("success", true); m.put("message", msg); return m; }
    private Map<String, Object> fail(String msg) { Map<String, Object> m = new LinkedHashMap<>(); m.put("success", false); m.put("message", msg); return m; }
}
