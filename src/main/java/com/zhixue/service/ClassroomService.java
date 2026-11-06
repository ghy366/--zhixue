package com.zhixue.service;

import com.zhixue.entity.Classroom;
import com.zhixue.repository.ClassroomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClassroomService {

    @Autowired private ClassroomRepository classroomRepo;

    public List<Classroom> findAll() {
        return classroomRepo.findAll();
    }

    public Classroom save(Classroom c) {
        return classroomRepo.save(c);
    }

    public void delete(Long id) {
        classroomRepo.deleteById(id);
    }

    public Classroom findById(Long id) {
        return id == null ? null : classroomRepo.findById(id).orElse(null);
    }

    public Classroom findByRoomNo(String roomNo) {
        return classroomRepo.findByRoomNo(roomNo).orElse(null);
    }
}
