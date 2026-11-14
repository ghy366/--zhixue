package com.zhixue.repository;

import com.zhixue.entity.Waitlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {
    List<Waitlist> findByRound(Integer round);
    List<Waitlist> findByCourseIdAndRoundAndStatusInOrderByJoinTimeAsc(Long courseId, Integer round, List<String> statuses);
    List<Waitlist> findByStudentIdAndRoundAndStatusInOrderByJoinTimeAsc(Long studentId, Integer round, List<String> statuses);
    Optional<Waitlist> findByStudentIdAndCourseIdAndRound(Long studentId, Long courseId, Integer round);
    List<Waitlist> findByStatusAndDeadlineBefore(String status, LocalDateTime deadline);
    List<Waitlist> findByCourseIdAndStatusIn(Long courseId, List<String> statuses);

    /** 新学期归档：只改状态、不删行，旧候补不再参与新学期递补 */
    @Modifying
    @Query(value = "update waitlists set status = 'expired' where status in ('waiting','confirming')", nativeQuery = true)
    int archiveActive();
}
