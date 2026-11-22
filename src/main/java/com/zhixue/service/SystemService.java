package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.GradeRequirement;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.TrainingPlanCourse;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.GradeRequirementRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.TrainingPlanCourseRepository;
import com.zhixue.util.CourseGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemService {

    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private GradeRequirementRepository gradeReqRepo;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private TrainingPlanCourseRepository trainingPlanRepo;
    @Autowired private CourseRepository courseRepo;

    public SystemConfig getConfig() { return enrollmentService.getSystemConfig(); }

    /** 已完成轮次数（0..3），兼容旧数据 NULL */
    public int roundProgress() { return progressOf(getConfig()); }

    private int progressOf(SystemConfig c) {
        Integer v = c.getRoundProgress();
        return v == null ? 0 : Math.max(0, Math.min(3, v));
    }

    /** 当前允许开启 / 切换到的最高轮次 */
    public int unlockedRound() { return Math.min(3, roundProgress() + 1); }

    /**
     * 轮次门控：返回拒绝原因，null 表示允许。
     * 第 1 轮需先「开启新学期」；第 2/3 轮需前一轮已关闭并结算（roundProgress 推进）。
     * 不允许跳级切轮，也不允许重复开启已完成的轮次。
     */
    public String roundGateError(int target, boolean opening) {
        SystemConfig c = getConfig();
        int unlocked = unlockedRound();
        if (target < 1 || target > 3) return "选课轮次只能是 1 ~ 3 轮";
        if (target > unlocked)
            return "第 " + target + " 轮尚未解锁：请先完成第 " + unlocked
                    + " 轮（关闭本轮即自动结算并解锁下一轮）";
        if (opening && target < unlocked)
            return "第 " + target + " 轮已完成，不能重复开启；请切换到第 " + unlocked + " 轮";
        if (opening && target == 1 && !Boolean.TRUE.equals(c.getNewSemesterReady()))
            return "请先开启新学期：到「学期管理」点「推进到下一学期」，或在本页点「进入新学期」，再开启第 1 轮选课";
        return null;
    }

    public SystemConfig setRound(int round) {
        SystemConfig c = getConfig();
        String err = roundGateError(round, false);
        if (err != null) throw new IllegalArgumentException(err);
        if (Boolean.TRUE.equals(c.getRoundOpen()))
            throw new IllegalArgumentException("请先关闭本轮选课，再切换轮次（关闭时会结算并解锁下一轮）");
        c.setCurrentRound(round);
        c.setAutoSchedule(false);   // 手动切换即退出自动模式，避免被时间窗口立即覆盖
        return sysRepo.save(c);
    }

    public SystemConfig setRoundOpen(boolean open) {
        SystemConfig c = getConfig();
        int round = c.getCurrentRound() == null ? 1 : c.getCurrentRound();
        c.setRoundOpen(open);
        c.setAutoSchedule(false);   // 手动开关即退出自动模式
        SystemConfig saved = sysRepo.save(c);
        if (!open) {
            settleCurrentRound(round);
            // 关闭本轮 = 完成本轮，解锁下一轮（只前进不后退）
            if (round > progressOf(saved)) {
                saved.setRoundProgress(round);
                sysRepo.save(saved);
            }
        }
        return saved;
    }

    /**
     * 手动关闭本轮时自动结算当前轮的待录取报名：选课截止即出结果，
     * 与自动排班「到点关闭并结算」保持同一语义（修复：关闭本轮后报名一直挂在待录取）。
     * 结算失败不阻断关闭动作，留下日志后可由管理员在「选课轮次 → 立即结算」重试。
     */
    private void settleCurrentRound(int round) {
        try {
            if (round >= 1 && round <= 2) {
                enrollmentService.settleRound(round);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(SystemService.class)
                    .warn("第 {} 轮关闭后自动结算失败：{}", round, e.getMessage(), e);
        }
    }

    public SystemConfig setConfirmMinutes(int minutes) {
        SystemConfig c = getConfig();
        c.setConfirmMinutes(minutes);
        return sysRepo.save(c);
    }

    public SystemConfig setSemesterOffset(Integer v) {
        SystemConfig c = getConfig();
        c.setSemesterOffset(v == null || v < 0 ? 0 : v);
        return sysRepo.save(c);
    }

    /** 开启第 1 轮后消费「新学期已开启」标记，防止重复开轮跳过学期推进 */
    public SystemConfig consumeNewSemester() {
        SystemConfig c = getConfig();
        c.setNewSemesterReady(false);
        return sysRepo.save(c);
    }

    public SystemConfig setTeacherMaxCourses(Integer v) {
        SystemConfig c = getConfig();
        c.setTeacherMaxCourses(v == null || v < 1 ? 3 : v);
        return sysRepo.save(c);
    }

    public SystemConfig setMaxCredit(int maxCredit) {
        SystemConfig c = getConfig();
        c.setMaxCreditPerSemester(maxCredit);
        return sysRepo.save(c);
    }

    public SystemConfig setMaxCourses(int maxCourses) {
        SystemConfig c = getConfig();
        c.setMaxCoursesPerSemester(maxCourses);
        return sysRepo.save(c);
    }

    /** 补考/重修参数：封顶分、补考次数、重修上限、组班门槛、补考报名窗口 */
    public SystemConfig setMakeupConfig(Integer scoreCap, Integer makeupMax, Integer retakeMax,
                                        Integer groupThreshold, java.time.LocalDateTime openAt,
                                        java.time.LocalDateTime closeAt) {
        SystemConfig c = getConfig();
        if (scoreCap != null) c.setMakeupScoreCap(scoreCap);
        if (makeupMax != null) c.setMakeupMaxAttempts(Math.max(1, makeupMax));
        if (retakeMax != null) c.setMaxRetakeAttempts(Math.max(1, retakeMax));
        if (groupThreshold != null) c.setRetakeGroupThreshold(Math.max(1, groupThreshold));
        if (openAt != null) c.setMakeupOpenAt(openAt);
        if (closeAt != null) c.setMakeupCloseAt(closeAt);
        return sysRepo.save(c);
    }

    public SystemConfig setGraduationReqs(Integer required, Integer elective, Integer general, Integer practice, Integer courses) {
        SystemConfig c = getConfig();
        if (required != null) c.setGradReqRequired(required);
        if (elective != null) c.setGradReqElective(elective);
        if (general != null) c.setGradReqGeneral(general);
        if (practice != null) c.setGradReqPractice(practice);
        if (courses != null) c.setGradReqCourses(courses);
        return sysRepo.save(c);
    }

    /* ---------- 毕业条件：学制两套（普通本科 / 专升本），即培养方案，不按年级分化 ---------- */

    /**
     * 毕业条件清单：学制两套——「普通本科」与「专升本」，每套固定一行。
     * 必修/专业选修分母全部由培养方案推导（修满方案即达标），公共选修按全校课程池展示（公选由学生自选）。
     */
    public List<Map<String, Object>> listGradeReqs() {
        int gxTotal = 0;
        for (Course c : courseRepo.findAll()) {
            if (c.getCategory() != null && c.getCategory().contains("公共选修")
                    && CourseGroup.countsForGraduation(c.getCourseCode())) {
                gxTotal += c.getCredit() == null ? 0 : c.getCredit();
            }
        }
        List<GradeRequirement> all = gradeReqRepo.findAllByOrderByGradeAsc();
        GradeRequirement normal = all.stream()
                .filter(g -> g.getLabel() == null || !g.getLabel().contains("专升本")).findFirst().orElse(null);
        GradeRequirement zsb = all.stream()
                .filter(g -> g.getLabel() != null && g.getLabel().contains("专升本")).findFirst().orElse(null);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        out.add(trackRow("normal", "普通本科", gxTotal));
        out.add(trackRow("zsb", "专升本", gxTotal));
        return out;
    }

    /** 一套学制的毕业条件行：分母按覆盖专业的培养方案逐专业推导，并附每专业的精确毕业条件 */
    private Map<String, Object> trackRow(String track, String label, int gxTotal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("track", track);
        m.put("label", label);
        // 覆盖专业：普通本科 = 不带专升本后缀的方案专业；专升本 = 带「专升本」的方案专业
        java.util.Set<String> covered = new java.util.LinkedHashSet<>();
        for (TrainingPlanCourse t : trainingPlanRepo.findByEnabledTrue()) {
            if (t.getMajor() == null || t.getMajor().isBlank()) continue;
            boolean zsbMajor = t.getMajor().contains("专升本");
            if (("zsb".equals(track)) == zsbMajor) covered.add(t.getMajor().trim());
        }
        // 每个专业的精确毕业条件（必修 / 专业选修 / 门数），合计 = 必修 + 专业选修 + 公选 6
        java.util.List<Map<String, Object>> majors = new java.util.ArrayList<>();
        Integer bxMin = null, bxMax = null, zxMin = null, zxMax = null;
        for (String cm : covered) {
            Map<String, Integer> sup = planSupplyOf(cm);
            int bx = sup.get("bx"), zx = sup.get("zx");
            bxMin = bxMin == null ? bx : Math.min(bxMin, bx);
            bxMax = bxMax == null ? bx : Math.max(bxMax, bx);
            zxMin = zxMin == null ? zx : Math.min(zxMin, zx);
            zxMax = zxMax == null ? zx : Math.max(zxMax, zx);
            Map<String, Object> mj = new LinkedHashMap<>();
            mj.put("major", cm);
            mj.put("bx", sup.get("bx"));
            mj.put("zx", sup.get("zx"));
            mj.put("courses", sup.get("courses"));
            mj.put("total", bx + zx + 6);
            majors.add(mj);
        }
        majors.sort(java.util.Comparator.comparingInt(o -> -((Number) o.get("total")).intValue()));
        if (bxMin != null) { m.put("planBxMin", bxMin); m.put("planBxMax", bxMax); }
        if (zxMin != null) { m.put("planZxMin", zxMin); m.put("planZxMax", zxMax); }
        m.put("gxSupply", gxTotal);
        m.put("coveredMajorCount", covered.size());
        m.put("majors", majors);
        return m;
    }

    /** 某专业培养方案的学分供给：必修 / 可计专业选修 / 课程门数（非 PE-，可计入毕业学分） */
    private Map<String, Integer> planSupplyOf(String major) {
        int bx = 0, zx = 0, courses = 0;
        // 与 GraduationService 的毕业审核口径一致：按课程名称去重。
        // 方案里存在同名不同 course_id 的重复条目（如机器人工程"嵌入式系统"1322/4401），
        // 若按 id 去重会把重复学分计入毕业条件表，造成与学生实际审核分母不一致。
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TrainingPlanCourse t : trainingPlanRepo.findApplicable(major, null)) {
            Course c = courseRepo.findById(t.getCourseId()).orElse(null);
            if (c == null || !CourseGroup.countsForGraduation(c.getCourseCode())) continue;
            if (!seen.add(c.getName() == null ? ("id" + c.getId()) : c.getName())) continue;
            String cat = c.getCategory() == null ? "" : c.getCategory();
            int cr = c.getCredit() == null ? 0 : c.getCredit();
            if (cat.contains("必修")) bx += cr;
            else if (cat.contains("专业选修")) zx += cr;
            courses++;
        }
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("bx", bx); m.put("zx", zx); m.put("courses", courses);
        return m;
    }

    /**
     * 保存两套学制的毕业要求：以 label（普通本科 / 专升本）upsert，
     * 年级与专业字段不再使用（置空）——毕业要求不按年级/专业分化。
     */
    @Transactional
    public Map<String, Object> saveGradeReqs(List<GradeRequirement> items) {
        Map<String, Object> r = new LinkedHashMap<>();
        GradeRequirement normal = null, zsb = null;
        for (GradeRequirement g : items == null ? List.<GradeRequirement>of() : items) {
            boolean zsbRow = g.getLabel() != null && g.getLabel().contains("专升本");
            if (zsbRow) {
                if (zsb != null) { r.put("success", false); r.put("msg", "专升本方案配置重复"); return r; }
                zsb = g;
            } else {
                if (normal != null) { r.put("success", false); r.put("msg", "普通本科方案配置重复"); return r; }
                normal = g;
            }
        }
        if (normal == null) { r.put("success", false); r.put("msg", "缺少普通本科方案"); return r; }
        if (zsb == null) { r.put("success", false); r.put("msg", "缺少专升本方案"); return r; }
        gradeReqRepo.deleteAll();
        saveTrack(null, "普通本科", normal);
        saveTrack(null, "专升本", zsb);
        r.put("success", true);
        r.put("msg", "毕业要求已保存（普通本科 + 专升本 两套）");
        return r;
    }

    private void saveTrack(Integer grade, String label, GradeRequirement g) {
        GradeRequirement e = new GradeRequirement();
        e.setGrade(grade);          // 毕业要求不再按年级分化，置空
        e.setMajor(null);
        e.setLabel(label);
        e.setGradReqRequired(nz(g.getGradReqRequired()));
        e.setGradReqGeneral(nz(g.getGradReqGeneral()));
        e.setGradReqElective(nz(g.getGradReqElective()));
        e.setGradReqPractice(0);
        e.setGradReqCourses(0);
        gradeReqRepo.save(e);
    }

    @Transactional
    public Map<String, Object> deleteGradeReq(Long id) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (!gradeReqRepo.existsById(id)) { r.put("success", false); r.put("msg", "配置不存在"); return r; }
        gradeReqRepo.deleteById(id);
        r.put("success", true);
        r.put("msg", "已删除该年级配置（该年级回退为全校默认要求）");
        return r;
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    public SystemConfig setPhase(SystemConfig body) {
        SystemConfig cfg = sysRepo.findById(1L).orElseGet(SystemConfig::new);
        if (body.getRoundOpenAt()     != null) cfg.setRoundOpenAt(body.getRoundOpenAt());
        if (body.getRoundCloseAt()    != null) cfg.setRoundCloseAt(body.getRoundCloseAt());
        if (body.getBatchOpenAt()     != null) cfg.setBatchOpenAt(body.getBatchOpenAt());
        if (body.getBatchCloseAt()    != null) cfg.setBatchCloseAt(body.getBatchCloseAt());
        if (body.getReselectOpenAt()  != null) cfg.setReselectOpenAt(body.getReselectOpenAt());
        if (body.getReselectCloseAt() != null) cfg.setReselectCloseAt(body.getReselectCloseAt());
        if (body.getCurrentPhase()    != null) cfg.setCurrentPhase(body.getCurrentPhase());
        return sysRepo.save(cfg);
    }
}
