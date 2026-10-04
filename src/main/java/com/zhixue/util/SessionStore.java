package com.zhixue.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 简易会话管理：token -> userId
public class SessionStore {
    private static final Map<String, Long> store = new ConcurrentHashMap<>();

    public static String createToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, userId);
        return token;
    }

    public static Long getUserId(String token) {
        if (token == null) return null;
        return store.get(token);
    }

    public static void remove(String token) {
        if (token == null) return;
        store.remove(token);
    }
}
