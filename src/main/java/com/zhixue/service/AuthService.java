package com.zhixue.service;

import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    @Autowired private UserRepository userRepo;

    public User login(String no, String password) {
        return userRepo.findByNoAndPassword(no, password).orElse(null);
    }

    public User registerStudent(Map<String, String> data) {
        String no = data.get("no");
        if (userRepo.findByNo(no).isPresent()) return null;
        User u = new User();
        u.setNo(no);
        u.setPassword(data.get("password"));
        u.setName(data.get("name"));
        u.setRole("student");
        u.setCollege(data.get("college"));
        u.setMajor(data.get("major"));
        u.setGrade(Integer.parseInt(data.getOrDefault("grade", "2023")));
        u.setClassNo(data.get("classNo"));
        u.setPhone(data.get("phone"));
        return userRepo.save(u);
    }

    public User findById(Long id) {
        return userRepo.findById(id).orElse(null);
    }
}