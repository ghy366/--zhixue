package com.zhixue.repository;

import com.zhixue.entity.GraduationApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GraduationApplicationRepository extends JpaRepository<GraduationApplication, Long> {

    Optional<GraduationApplication> findFirstByStudentIdOrderByIdDesc(Long studentId);

    List<GraduationApplication> findAllByOrderByIdDesc();
}
