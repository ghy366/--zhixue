package com.zhixue.service;

import com.zhixue.entity.Grade;
import com.zhixue.repository.GradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GradeService {

    @Autowired private GradeRepository gradeRepo;
    @Autowired private com.zhixue.repository.CourseRepository courseRepo;
    @Autowired private com.zhixue.repository.UserRepository userRepo;

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
    public Grade save(Long studentId, Long courseId, Double regular, Double finalScore, String examType) {
        Grade g = gradeRepo.findByStudentIdAndCourseId(studentId, courseId).orElseGet(Grade::new);
        boolean existed = g.getId() != null;
        String type = examType == null || examType.isBlank()
                ? (existed && g.getExamType() != null ? g.getExamType() : "normal")
                : examType;
        boolean newAttempt = existed && !type.equals(g.getExamType());
        g.setStudentId(studentId);
        g.setCourseId(courseId);
        g.setRegularScore(regular);
        g.setFinalScore(finalScore);
        // 总评 = 平时*40% + 期末*60%
        double total = 0;
        if (regular != null) total += regular * 0.4;
        if (finalScore != null) total += finalScore * 0.6;
        g.setTotalScore(Math.round(total * 10.0) / 10.0);
        g.setExamType(type);
        if (g.getSemesterNo() == null) {
            // 成绩归属学期 = 课程培养方案学期；未排期课程（选修等）归当前学期
            g.setSemesterNo(courseRepo.findById(courseId)
                    .map(c -> c.getSemesterNo() != null && c.getSemesterNo() > 0 && c.getSemesterNo() < 7
                            ? c.getSemesterNo() : 7).orElse(7));
        }
        g.setAttempt(existed ? Math.max(1, g.getAttempt() == null ? 1 : g.getAttempt()) + (newAttempt ? 1 : 0) : 1);
        return gradeRepo.save(g);
    }

    /** 是否已通过（总评 ≥ 60） */
    public static boolean passed(Grade g) {
        return g != null && g.getTotalScore() != null && g.getTotalScore() >= PASS_LINE;
    }

    /** 按课程学分加权累计 GPA。totalScore 换算 4.0 制：>=90=4, >=85=3.7, >=80=3.3, >=75=3, >=70=2.7, >=65=2.3, >=60=2 */
    public Map<String, Object> gpaOfStudent(Long studentId) {
        Map<String, Object> r = new HashMap<>();
        List<Grade> list = findByStudent(studentId);
        if (list == null || list.isEmpty()) {
            r.put("gpa", 0.0); r.put("totalCredit", 0); r.put("courseCount", 0);
            r.put("detail", new java.util.ArrayList<>());
            return r;
        }
        double weighted = 0; int totalCredit = 0;
        java.util.List<Map<String, Object>> detail = new java.util.ArrayList<>();
        for (Grade g : list) {
            if (g.getTotalScore() == null) continue;
            int credit = courseRepo.findById(g.getCourseId()).map(c -> c.getCredit() == null ? 0 : c.getCredit()).orElse(3);
            // 真实高校规则：补考通过一律按 60 分档计绩点（重修按实际分数），防止刷分
            double pts = "makeup".equals(g.getExamType())
                    ? Math.min(2.0, scoreToGpa(g.getTotalScore()))
                    : scoreToGpa(g.getTotalScore());
            weighted += pts * credit;
            totalCredit += credit;
            Map<String, Object> item = new HashMap<>();
            item.put("courseId", g.getCourseId());
            item.put("totalScore", g.getTotalScore());
            item.put("examType", g.getExamType());
            item.put("points", pts);
            item.put("credit", credit);
            detail.add(item);
        }
        double gpa = totalCredit > 0 ? Math.round((weighted / totalCredit) * 100.0) / 100.0 : 0.0;
        r.put("gpa", gpa);
        r.put("totalCredit", totalCredit);
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
        // 一次性聚合所有成绩：courseId -> (studentId -> bestScore)
        Map<Long, Map<Long, Double>> scoreByCourse = new HashMap<>();
        Map<Long, Integer> creditByCourse = new HashMap<>();
        for (Grade g : gradeRepo.findAll()) {
            if (g.getTotalScore() == null) continue;
            scoreByCourse.computeIfAbsent(g.getCourseId(), k -> new HashMap<>())
                    .merge(g.getStudentId(), g.getTotalScore(), Math::max);
        }
        courseRepo.findAll().forEach(c -> creditByCourse.put(c.getId(), c.getCredit() == null ? 0 : c.getCredit()));
        // 每个同专业学生的加权 GPA（挂科 0 分计入、重修补考覆盖口径与个人页一致）
        List<Double> gpas = new java.util.ArrayList<>();
        double myGpa = 0;
        for (com.zhixue.entity.User u : peers) {
            double w = 0; int credits = 0;
            for (Map.Entry<Long, Map<Long, Double>> e : scoreByCourse.entrySet()) {
                Double sc = e.getValue().get(u.getId());
                if (sc == null) continue;
                int credit = creditByCourse.getOrDefault(e.getKey(), 0);
                if (credit <= 0) continue;
                // 补考通过按 60 分档计绩点，与个人 GPA 口径一致
                Grade probe = new Grade();
                probe.setTotalScore(sc);
                String examType = null; // 聚合口径按实际分数；补考封顶已计入学生真实最新成绩行
                double pts = scoreToGpa(sc);
                w += pts * credit;
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
