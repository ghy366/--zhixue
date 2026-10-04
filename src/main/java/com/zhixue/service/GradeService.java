package com.zhixue.service;

import com.zhixue.entity.Grade;
import com.zhixue.repository.GradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
