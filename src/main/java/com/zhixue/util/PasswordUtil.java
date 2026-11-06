package com.zhixue.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 统一密码加密工具（BCrypt）。
 *
 * 存储格式：$2a$10$...（60 字符 BCrypt 哈希，自带随机盐）。
 * 兼容历史明文：存量数据中尚未哈希的密码在验证时按明文比对，
 * 由迁移任务 / 登录兜底逻辑逐步升级为哈希。
 */
@Component
public class PasswordUtil {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 明文 → BCrypt 哈希（每次生成随机盐，同一明文哈希不同） */
    public String encode(String rawPassword) {
        if (rawPassword == null) return null;
        return encoder.encode(rawPassword);
    }

    /** 是否已是哈希格式（$2 开头） */
    public boolean isHashed(String stored) {
        return stored != null && stored.length() == 60 && stored.startsWith("$2");
    }

    /**
     * 校验明文密码：哈希用户走 BCrypt 比对；
     * 历史明文用户直接字符串比对（调用方可据返回值触发升级哈希）。
     */
    public boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) return false;
        if (isHashed(stored)) {
            try {
                return encoder.matches(rawPassword, stored);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return stored.equals(rawPassword);
    }
}
