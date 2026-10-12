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

    public List<Grade> findByCourse(Long courseId) {
        return gradeRepo.findByCourseId(courseId);
    }

    public Grade findByStudentAndCourse(Long studentId, Long courseId) {
        return gradeRepo.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
    }

    public List<Grade> findByStudent(Long studentId) {
        return gradeRepo.findByStudentId(studentId);
    }

    public Grade save(Long studentId, Long courseId, Double regular, Double finalScore) {
        Grade g = gradeRepo.findByStudentIdAndCourseId(studentId, courseId).orElseGet(Grade::new);
        g.setStudentId(studentId);
        g.setCourseId(courseId);
        g.setRegularScore(regular);
        g.setFinalScore(finalScore);
        // 总评 = 平时*40% + 期末*60%
        double total = 0;
        if (regular != null) total += regular * 0.4;
        if (finalScore != null) total += finalScore * 0.6;
        g.setTotalScore(Math.round(total * 10.0) / 10.0);
        return gradeRepo.save(g);
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
            int credit = 3;  // 默认 3 分
            double pts = scoreToGpa(g.getTotalScore());
            weighted += pts * credit;
            totalCredit += credit;
            Map<String, Object> item = new HashMap<>();
            item.put("courseId", g.getCourseId());
            item.put("totalScore", g.getTotalScore());
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
}
