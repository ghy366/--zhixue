package com.zhixue.repository;

import com.zhixue.entity.GradeRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GradeRequirementRepository extends JpaRepository<GradeRequirement, Long> {

    List<GradeRequirement> findByGrade(Integer grade);

    List<GradeRequirement> findAllByOrderByGradeAsc();
}
