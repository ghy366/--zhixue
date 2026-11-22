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

    public User findById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    /** 供控制器复用的密码校验/加密入口 */
    public boolean matches(String raw, String stored) { return passwordUtil.matches(raw, stored); }
    public String encode(String raw) { return passwordUtil.encode(raw); }
}