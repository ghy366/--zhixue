package com.zhixue.repository;

import com.zhixue.entity.StaggerWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StaggerWindowRepository extends JpaRepository<StaggerWindow, Long> {
    List<StaggerWindow> findByRoundNoOrderByGradeAsc(Integer roundNo);
    Optional<StaggerWindow> findByRoundNoAndGrade(Integer roundNo, Integer grade);
}
