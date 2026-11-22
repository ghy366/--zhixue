package com.zhixue.repository;

import com.zhixue.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByNoAndPassword(String no, String password);
    Optional<User> findByNo(String no);
    List<User> findByRole(String role);

    /**
     * 锁定学生行：同一学生的并发选课/退课/改选请求在此串行化，
     * 避免“先查后插”产生重复报名或积分超扣。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** 按年级统计在读学生数：教务端「全校当前学期」以人数最多的年级为基准 */
    @Query("select u.grade, count(u) from User u where u.role = 'student' and u.grade is not null group by u.grade")
    List<Object[]> countStudentsByGrade();
}
