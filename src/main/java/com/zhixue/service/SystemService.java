package com.zhixue.service;

import com.zhixue.entity.GradeRequirement;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.GradeRequirementRepository;
import com.zhixue.repository.SystemConfigRepository;
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

    public SystemConfig setGraduationReqs(Integer required, Integer elective, Integer general, Integer practice, Integer courses) {
        SystemConfig c = getConfig();
        if (required != null) c.setGradReqRequired(required);
        if (elective != null) c.setGradReqElective(elective);
        if (general != null) c.setGradReqGeneral(general);
        if (practice != null) c.setGradReqPractice(practice);
        if (courses != null) c.setGradReqCourses(courses);
        return sysRepo.save(c);
    }

    /* ---------- 按年级差异化毕业要求 ---------- */

    public List<GradeRequirement> listGradeReqs() {
        return gradeReqRepo.findAllByOrderByGradeAsc();
    }

    /** 整表保存：以提交列表为准全量替换（年级数量少，替换最直观且不易残留脏行） */
    @Transactional
    public Map<String, Object> saveGradeReqs(List<GradeRequirement> items) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (items == null) items = List.of();
        for (GradeRequirement g : items) {
            if (g.getGrade() == null) { r.put("success", false); r.put("msg", "存在未填写年级的配置行"); return r; }
            if (g.getGrade() < 2000 || g.getGrade() > 2100) { r.put("success", false); r.put("msg", "年级应为入学年份（如 2023）"); return r; }
        }
        // 同一「年级+专业」组合只能有一行（major 为空 = 该年级通用方案）
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (GradeRequirement g : items) {
            String key = g.getGrade() + ":" + (g.getMajor() == null ? "" : g.getMajor().trim());
            if (!seen.add(key)) { r.put("success", false); r.put("msg", "年级 " + g.getGrade() + (g.getMajor() == null || g.getMajor().isBlank() ? "" : "（" + g.getMajor() + "）") + " 配置重复"); return r; }
        }
        gradeReqRepo.deleteAll();
        for (GradeRequirement g : items) {
            GradeRequirement e = new GradeRequirement();
            e.setGrade(g.getGrade());
            e.setMajor(g.getMajor() == null || g.getMajor().isBlank() ? null : g.getMajor().trim());
            e.setLabel(g.getLabel() == null ? "" : g.getLabel().trim());
            e.setGradReqRequired(nz(g.getGradReqRequired()));
            e.setGradReqElective(nz(g.getGradReqElective()));
            e.setGradReqGeneral(nz(g.getGradReqGeneral()));
            e.setGradReqPractice(nz(g.getGradReqPractice()));
            e.setGradReqCourses(nz(g.getGradReqCourses()));
            gradeReqRepo.save(e);
        }
        r.put("success", true);
        r.put("msg", "培养方案已保存（" + items.size() + " 行）");
        return r;
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
