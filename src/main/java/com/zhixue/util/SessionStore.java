package com.zhixue.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 简易会话管理：token -> userId + role（角色由登录时写入，供统一鉴权拦截器校验，不必每请求查库）
public class SessionStore {
    /** token 有效期 12 小时（毫秒）；过期后需重新登录，避免 token 永久有效 */
    private static final long TTL_MS = 12L * 60 * 60 * 1000;
    private static final Map<String, Long> store = new ConcurrentHashMap<>();
    private static final Map<String, String> roles = new ConcurrentHashMap<>();
    private static final Map<String, Long> createdAt = new ConcurrentHashMap<>();

    public static String createToken(Long userId) {
        return createToken(userId, null);
    }

    /** 登录发 token 时同时登记角色；role 为空时由拦截器兜底查库 */
    public static String createToken(Long userId, String role) {
        String token = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        store.put(token, userId);
        createdAt.put(token, now);
        if (role != null) roles.put(token, role);
        // 每次发 token 时顺带清理过期会话，避免长期运行内存泄漏
        purgeExpired(now);
        return token;
    }

    public static Long getUserId(String token) {
        if (token == null) return null;
        Long t = createdAt.get(token);
        if (t == null || System.currentTimeMillis() - t > TTL_MS) {
            remove(token);   // 过期或缺失创建时间 → 按失效处理
            return null;
        }
        return store.get(token);
    }

    public static String getRole(String token) {
        if (token == null) return null;
        // 角色读取也要确保未过期（拦截器在 getUserId 之后调用，通常已校验，独立调用也安全）
        Long t = createdAt.get(token);
        if (t == null || System.currentTimeMillis() - t > TTL_MS) return null;
        return roles.get(token);
    }

    public static void remove(String token) {
        if (token == null) return;
        store.remove(token);
        roles.remove(token);
        createdAt.remove(token);
    }

    /** 清理所有过期 token（惰性触发：登录时执行，无需额外调度器） */
    private static void purgeExpired(long now) {
        for (Map.Entry<String, Long> e : createdAt.entrySet()) {
            if (now - e.getValue() > TTL_MS) remove(e.getKey());
        }
    }
}
