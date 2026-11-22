package com.zhixue.repository;

import com.zhixue.entity.GraduatePlagiarism;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GraduatePlagiarismRepository extends JpaRepository<GraduatePlagiarism, Long> {

    Optional<GraduatePlagiarism> findByAdvisorId(Long advisorId);

    List<GraduatePlagiarism> findAll();
}
