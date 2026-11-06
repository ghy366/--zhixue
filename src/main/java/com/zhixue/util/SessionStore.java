package com.zhixue.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 简易会话管理：token -> userId + role（角色由登录时写入，供统一鉴权拦截器校验，不必每请求查库）
public class SessionStore {
    private static final Map<String, Long> store = new ConcurrentHashMap<>();
    private static final Map<String, String> roles = new ConcurrentHashMap<>();

    public static String createToken(Long userId) {
        return createToken(userId, null);
    }

    /** 登录发 token 时同时登记角色；role 为空时由拦截器兜底查库 */
    public static String createToken(Long userId, String role) {
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, userId);
        if (role != null) roles.put(token, role);
        return token;
    }

    public static Long getUserId(String token) {
        if (token == null) return null;
        return store.get(token);
    }

    public static String getRole(String token) {
        if (token == null) return null;
        return roles.get(token);
    }

    public static void remove(String token) {
        if (token == null) return;
        store.remove(token);
        roles.remove(token);
    }
}
