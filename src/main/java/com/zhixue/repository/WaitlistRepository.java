package com.zhixue.repository;

import com.zhixue.entity.Waitlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {
    List<Waitlist> findByCourseIdAndRoundAndStatusInOrderByJoinTimeAsc(Long courseId, Integer round, List<String> statuses);
    List<Waitlist> findByStudentIdAndRoundAndStatusInOrderByJoinTimeAsc(Long studentId, Integer round, List<String> statuses);
    Optional<Waitlist> findByStudentIdAndCourseIdAndRound(Long studentId, Long courseId, Integer round);
    List<Waitlist> findByStatusAndDeadlineBefore(String status, LocalDateTime deadline);
    List<Waitlist> findByCourseIdAndStatusIn(Long courseId, List<String> statuses);
}
