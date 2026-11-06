package com.zhixue.service;

import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {

    @Autowired private UserRepository userRepo;
    @Autowired private com.zhixue.util.PasswordUtil passwordUtil;

    public User login(String no, String password) {
        if (no == null || password == null) return null;
        User u = userRepo.findByNo(no).orElse(null);
        if (u == null || !passwordUtil.matches(password, u.getPassword())) return null;
        // 历史明文密码在登录成功后自动升级为 BCrypt 哈希（幂等）
        if (!passwordUtil.isHashed(u.getPassword())) {
            try {
                u.setPassword(passwordUtil.encode(password));
                userRepo.save(u);
            } catch (Exception ignored) {}
        }
        return u;
    }

    public User registerStudent(Map<String, String> data) {
        String no = data.get("no");
        if (userRepo.findByNo(no).isPresent()) return null;
        // 年级 = 入学年份（如 2023 表示 2023 级）：显式传值优先；
        // 未传时从学号前 4 位推导（2000-2100 范围内的合法年份才采用），推导不了则留空
        Integer grade = null;
        String g = data.get("grade");
        if (g != null && !g.isEmpty()) {
            try { grade = Integer.parseInt(g); } catch (NumberFormatException ignored) {}
        }
        if (grade == null && no != null && no.length() >= 4) {
            try {
                int y = Integer.parseInt(no.substring(0, 4));
                if (y >= 2000 && y <= 2100) grade = y;
            } catch (NumberFormatException ignored) {}
        }
        User u = new User();
        u.setNo(no);
        u.setPassword(passwordUtil.encode(data.get("password")));
        u.setName(data.get("name"));
        u.setRole("student");
        u.setCollege(data.get("college"));
        u.setMajor(data.get("major"));
        u.setGrade(grade);
        u.setClassNo(data.get("classNo"));
        u.setPhone(data.get("phone"));
        return userRepo.save(u);
    }

    public User findById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    /** 供控制器复用的密码校验/加密入口 */
    public boolean matches(String raw, String stored) { return passwordUtil.matches(raw, stored); }
    public String encode(String raw) { return passwordUtil.encode(raw); }
}