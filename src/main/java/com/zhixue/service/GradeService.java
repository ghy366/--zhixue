package com.zhixue.service;

import com.zhixue.entity.Grade;
import com.zhixue.repository.GradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GradeService {

    @Autowired private GradeRepository gradeRepo;
    @Autowired private com.zhixue.repository.CourseRepository courseRepo;
    @Autowired private com.zhixue.repository.UserRepository userRepo;
    @Autowired private GradeLedgerService ledger;

    public List<Grade> findByCourse(Long courseId) {
        return gradeRepo.findByCourseId(courseId);
    }

    public Grade findByStudentAndCourse(Long studentId, Long courseId) {
        return gradeRepo.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
    }

    public List<Grade> findByStudent(Long studentId) {
        return gradeRepo.findByStudentId(studentId);
    }

    /** 及格线：与毕业判定共用同一口径 */
    public static final double PASS_LINE = 60.0;

    public Grade save(Long studentId, Long courseId, Double regular, Double finalScore) {
        return save(studentId, courseId, regular, finalScore, null);
    }

    /**
     * 录入/更新成绩。
     * @param examType 成绩性质：normal 正常 / makeup 补考 / retake 重修；为空时保持原性质
     *                 （普通改分不会误标为补考，补考/重修必须由教师端显式标记）。
     *                 性质变化（如 正常→补考、补考→重修）时修读次数 +1，同性质改分不重复累计次数。
     */
    @Transactional
    public Grade save(Long studentId, Long courseId, Double regular, Double finalScore, String examType) {
        Grade g = gradeRepo.findByStudentIdAndCourseId(studentId, courseId).orElseGet(Grade::new);
        boolean existed = g.getId() != null;
        String type = GradeLedgerService.normalizeType(examType == null || examType.isBlank()
                ? (existed ? g.getExamType() : "normal")
                : examType);
        boolean newAttempt = existed && !type.equals(GradeLedgerService.normalizeType(g.getExamType()));
        g.setStudentId(studentId);
        g.setCourseId(courseId);
        g.setRegularScore(regular);
        g.setFinalScore(finalScore);
        // 总评 = 平时*40% + 期末*60%；补考按配置封顶（默认 60），重修按实际分数
        double total = 0;
        if (regular != null) total += regular * 0.4;
        if (finalScore != null) total += finalScore * 0.6;
        total = ledger.capScore(type, total);
        g.setTotalScore(Math.round(total * 10.0) / 10.0);
        g.setExamType(type);
        if (g.getSemesterNo() == null) {
            // 成绩归属学期 = 课程培养方案学期；未排期课程（选修等）归当前学期
            g.setSemesterNo(courseRepo.findById(courseId)
                    .map(c -> c.getSemesterNo() != null && c.getSemesterNo() > 0 && c.getSemesterNo() < 7
                            ? c.getSemesterNo() : 7).orElse(7));
        }
        g.setAttempt(existed ? Math.max(1, g.getAttempt() == null ? 1 : g.getAttempt()) + (newAttempt ? 1 : 0) : 1);
        Grade saved = gradeRepo.save(g);
        // 写成绩流水并推进补考/重修状态机（T1~T4）
        ledger.afterGradeSaved(saved);
        return saved;
    }

    /** 是否已通过（总评 ≥ 60） */
    public static boolean passed(Grade g) {
        return g != null && g.getTotalScore() != null && g.getTotalScore() >= PASS_LINE;
    }

    /**
     * 按课程学分加权累计 GPA（唯一口径：成绩流水的「有效记录」）。
     * 有效记录 = 最新一次通过优先，无通过记录取最新一次修读；挂科 0 绩点仍计入修读学分分母。
     */
    public Map<String, Object> gpaOfStudent(Long studentId) {
        Map<String, Object> r = new HashMap<>();
        Map<Long, com.zhixue.entity.GradeRecord> effective = ledger.effectiveRecords(studentId);
        if (effective.isEmpty()) {
            r.put("gpa", 0.0); r.put("totalCredit", 0); r.put("earnedCredit", 0); r.put("courseCount", 0);
            r.put("detail", new java.util.ArrayList<>());
            return r;
        }
        double weighted = 0; int totalCredit = 0, earnedCredit = 0;
        java.util.List<Map<String, Object>> detail = new java.util.ArrayList<>();
        for (Map.Entry<Long, com.zhixue.entity.GradeRecord> en : effective.entrySet()) {
            com.zhixue.entity.GradeRecord g = en.getValue();
            com.zhixue.entity.Course c = courseRepo.findById(en.getKey()).orElse(null);
            int credit = c == null || c.getCredit() == null ? 0 : c.getCredit();
            double pts = GradeLedgerService.pointsOf(g);
            boolean passed = g.getTotalScore() != null && g.getTotalScore() >= PASS_LINE;
            weighted += pts * credit;
            totalCredit += credit;
            if (passed) earnedCredit += credit;
            Map<String, Object> item = new HashMap<>();
            item.put("courseId", en.getKey());
            item.put("courseName", c != null ? c.getName() : null);
            item.put("totalScore", g.getTotalScore());
            item.put("examType", g.getExamType());
            item.put("attemptNo", g.getAttemptNo());
            item.put("points", pts);
            item.put("credit", credit);
            item.put("passed", passed);
            detail.add(item);
        }
        double gpa = totalCredit > 0 ? Math.round((weighted / totalCredit) * 100.0) / 100.0 : 0.0;
        r.put("gpa", gpa);
        r.put("totalCredit", totalCredit);
        r.put("earnedCredit", earnedCredit);
        r.put("courseCount", detail.size());
        r.put("detail", detail);
        return r;
    }

    /** 90→4  85→3.7  80→3.3  75→3  70→2.7  65→2.3  60→2  <60→0 */
    public static double scoreToGpa(double score) {
        if (score >= 90) return 4.0;
        if (score >= 85) return 3.7;
        if (score >= 80) return 3.3;
        if (score >= 75) return 3.0;
        if (score >= 70) return 2.7;
        if (score >= 65) return 2.3;
        if (score >= 60) return 2.0;
        return 0.0;
    }

    /**
     * 专业 GPA 排名：同一学院+专业（无专业则全学院）学生按「真实学分加权 GPA」降序，
     * 返回本人的名次/总人数/百分位与 GPA。一次拉全量成绩内存聚合，学生规模内开销可控。
     */
    public Map<String, Object> majorRanking(com.zhixue.entity.User me) {
        Map<String, Object> r = new HashMap<>();
        r.put("rank", null); r.put("total", 0); r.put("percentile", null); r.put("gpa", null);
        if (me == null) return r;
        // 同专业同学集合（专业为空退化为同学院）
        List<com.zhixue.entity.User> peers = userRepo.findAll().stream()
                .filter(u -> "student".equals(u.getRole()))
                .filter(u -> u.getMajor() != null && u.getMajor().equals(me.getMajor()))
                .toList();
        // 一次性聚合全校有效成绩（与个人页、毕业判定同一口径）与课程学分
        Map<Long, Map<Long, com.zhixue.entity.GradeRecord>> effectiveAll = ledger.effectiveRecordsAll();
        Map<Long, Integer> creditByCourse = new HashMap<>();
        courseRepo.findAll().forEach(c -> creditByCourse.put(c.getId(), c.getCredit() == null ? 0 : c.getCredit()));
        // 每个同专业学生的加权 GPA（有效记录：最新通过优先；挂科 0 绩点计入分母）
        List<Double> gpas = new java.util.ArrayList<>();
        double myGpa = 0;
        for (com.zhixue.entity.User u : peers) {
            double w = 0; int credits = 0;
            for (Map.Entry<Long, com.zhixue.entity.GradeRecord> e
                    : effectiveAll.getOrDefault(u.getId(), Map.of()).entrySet()) {
                int credit = creditByCourse.getOrDefault(e.getKey(), 0);
                if (credit <= 0) continue;
                w += GradeLedgerService.pointsOf(e.getValue()) * credit;
                credits += credit;
            }
            double gpa = credits > 0 ? w / credits : 0;
            gpas.add(gpa);
            if (u.getId().equals(me.getId())) myGpa = gpa;
        }
        if (!gpas.isEmpty()) {
            gpas.sort((a, b) -> Double.compare(b, a));
            int rank = 1;
            for (double g : gpas) { if (g > myGpa) rank++; }
            r.put("rank", rank);
            r.put("total", gpas.size());
            r.put("percentile", Math.round(rank * 10000.0 / gpas.size()) / 100.0);
            r.put("gpa", Math.round(myGpa * 100.0) / 100.0);
        }
        r.put("major", me.getMajor());
        return r;
    }
}
