package com.zhixue.repository;

import com.zhixue.entity.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByRoomNo(String roomNo);
    List<Classroom> findByStatus(String status);
}
