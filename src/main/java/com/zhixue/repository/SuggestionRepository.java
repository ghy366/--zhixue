package com.zhixue.repository;

import com.zhixue.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    List<Suggestion> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Suggestion> findAllByOrderByCreatedAtDesc();

    List<Suggestion> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);
}
