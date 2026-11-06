package com.zhixue.repository;

import com.zhixue.entity.Enrollment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentIdAndRound(Long studentId, Integer round);
    List<Enrollment> findByCourseIdAndRound(Long courseId, Integer round);
    Optional<Enrollment> findByStudentIdAndCourseIdAndRound(Long studentId, Long courseId, Integer round);
    List<Enrollment> findByStudentId(Long studentId);

    /** 跨轮次取某课程全部报名记录（协同过滤"也选了"用） */
    List<Enrollment> findByCourseId(Long courseId);

    /** 一次性取出某轮全部报名记录，供结算统一分配（避免逐门课查询产生 N+1） */
    List<Enrollment> findByRound(Integer round);

    /** 按 id 倒序取某轮最新的 N 条报名（战况流用） */
    List<Enrollment> findByRoundOrderByIdDesc(Integer round, Pageable pageable);

    /** 战况流：某轮最新报名，排除必修课与落选/退课记录，数据库层过滤 */
    @Query("select e from Enrollment e where e.round = ?1 " +
           "and e.status in ('approved','pending') " +
           "and e.courseId not in (select c.id from Course c where c.category like '%必修%') " +
           "order by e.id desc")
    List<Enrollment> findRecentElectiveByRound(Integer round, Pageable pageable);

    /** 是否还有未结算的报名记录，供定时任务快速短路 */
    boolean existsByRoundAndStatus(Integer round, String status);
}
