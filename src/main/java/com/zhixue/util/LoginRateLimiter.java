package com.zhixue.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流器（内存版，防撞库暴力破解）：
 * - 以「客户端 IP + 账号」为键，滑动窗口统计登录失败次数
 * - 窗口内失败达到上限（5 次 / 分钟）后，该组合锁定 5 分钟
 * - 登录成功即清零；条目惰性过期，无后台线程
 * 单机演示足够；多实例部署时应替换为 Redis 实现。
 */
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 60_000L;        // 统计窗口：1 分钟
    private static final long LOCK_MS = 5 * 60_000L;      // 触发后锁定：5 分钟

    private static final Map<String, Deque<Long>> FAILURES = new ConcurrentHashMap<>();
    private static final Map<String, Long> LOCKED_UNTIL = new ConcurrentHashMap<>();

    private LoginRateLimiter() {}

    private static String key(String ip, String account) {
        return ip + "|" + (account == null ? "" : account.trim().toLowerCase());
    }

    /** 是否处于锁定期内 */
    public static boolean isLocked(String ip, String account) {
        String k = key(ip, account);
        Long until = LOCKED_UNTIL.get(k);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;
        LOCKED_UNTIL.remove(k);
        FAILURES.remove(k);   // 锁定过期，连带清失败记录
        return false;
    }

    /** 记录一次登录失败；返回 true 表示本次失败触发了限流锁定 */
    public static boolean recordFailure(String ip, String account) {
        String k = key(ip, account);
        long now = System.currentTimeMillis();
        Deque<Long> q = FAILURES.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            q.addLast(now);
            if (q.size() >= MAX_FAILURES) {
                LOCKED_UNTIL.put(k, now + LOCK_MS);
                FAILURES.remove(k);
                return true;
            }
        }
        return false;
    }

    /** 登录成功：清空该组合的失败记录 */
    public static void reset(String ip, String account) {
        String k = key(ip, account);
        FAILURES.remove(k);
        LOCKED_UNTIL.remove(k);
    }
}
