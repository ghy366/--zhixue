package com.zhixue.repository;

import com.zhixue.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByNoAndPassword(String no, String password);
    Optional<User> findByNo(String no);
    List<User> findByRole(String role);
}
