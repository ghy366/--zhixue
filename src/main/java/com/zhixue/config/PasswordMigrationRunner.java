package com.zhixue.config;

import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时一次性迁移：把数据库中仍为明文的密码全部升级为 BCrypt 哈希。
 * 已是哈希格式（$2 开头）的记录跳过，幂等可重复执行。
 */
@Component
@Order(100) // 在 DataInitializer 之后执行，避免与种子数据写入竞争
public class PasswordMigrationRunner implements CommandLineRunner {

    @Autowired private UserRepository userRepo;
    @Autowired private PasswordUtil passwordUtil;

    @Override
    public void run(String... args) {
        try {
            List<User> users = userRepo.findAll();
            int migrated = 0;
            for (User u : users) {
                if (u.getPassword() != null && !passwordUtil.isHashed(u.getPassword())) {
                    u.setPassword(passwordUtil.encode(u.getPassword()));
                    userRepo.save(u);
                    migrated++;
                }
            }
            if (migrated > 0) {
                System.out.println("[PasswordMigration] 已将 " + migrated + " 个明文密码升级为 BCrypt 哈希");
            }
        } catch (Exception e) {
            System.err.println("[PasswordMigration] 密码迁移失败（不影响启动）: " + e.getMessage());
        }
    }
}
