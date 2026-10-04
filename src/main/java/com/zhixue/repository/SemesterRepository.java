package com.zhixue.repository;

import com.zhixue.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByIsCurrentTrue();
    List<Semester> findByStatus(String status);
}
