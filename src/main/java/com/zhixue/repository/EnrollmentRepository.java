package com.zhixue.repository;

import com.zhixue.entity.Enrollment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentIdAndRound(Long studentId, Integer round);
    List<Enrollment> findByCourseIdAndRound(Long courseId, Integer round);
    Optional<Enrollment> findByStudentIdAndCourseIdAndRound(Long studentId, Long courseId, Integer round);
    List<Enrollment> findByStudentId(Long studentId);

    /** 同一行政班（class_no）同学正在修的课程 ID —— 用于"必修课按班补齐"的判据 */
    @Query("select distinct e.courseId from Enrollment e where e.status <> 'dropped' and e.studentId in "
         + "(select u.id from User u where u.classNo = :classNo)")
    List<Long> findClassmateCourseIds(@Param("classNo") String classNo);

    /**
     * 同一行政班（class_no）同学正在修的「课程 + 学期」组合。
     * 培养方案挂的是课程目录行，而一个课程目录会拆成多个教学班行（同名不同 id），
     * 因此匹配必须按「课程名 + 学期」而不是课程 id，否则永远匹配不上、退化成把整套方案塞给学生。
     * 必须排除学生本人：否则本人首次排课之后，方案里新增的课程会因「本班没开出」而永远排不进去（自引用死锁）。
     */
    @Query("select distinct e.courseId, e.semesterNo from Enrollment e where e.status <> 'dropped' and e.studentId <> :studentId and e.studentId in "
         + "(select u.id from User u where u.classNo = :classNo and u.id <> :studentId)")
    List<Object[]> findClassmateCourseRows(@Param("classNo") String classNo, @Param("studentId") Long studentId);

    /** 跨轮次取某课程全部报名记录（协同过滤"也选了"用） */
    List<Enrollment> findByCourseId(Long courseId);

    /** 按课程+学期取报名记录：容量按「本学期跨轮次已录取」口径统计（修复跨轮超额录取） */
    List<Enrollment> findByCourseIdAndSemesterNo(Long courseId, Integer semesterNo);

    /** 一次性取出某学期全部报名记录（课程列表批量预取「学期池跨轮已录取」口径用） */
    List<Enrollment> findBySemesterNo(Integer semesterNo);

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

    /** 按状态计数（容量释放时统计真正让出的座位数） */
    long countByStatus(String status);

    /** 教学班成员（方案教学班：学生选课记录挂 teaching_class_id） */
    List<Enrollment> findByTeachingClassIdOrderByIdAsc(Long teachingClassId);

    /** 教学班在读人数（方案教学班） */
    long countByTeachingClassId(Long teachingClassId);
}
