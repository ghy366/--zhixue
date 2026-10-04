package com.zhixue.service;

import com.zhixue.entity.Notification;
import com.zhixue.entity.User;
import com.zhixue.repository.NotificationRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 站内消息中心 + SSE 实时推送 */
@Service
public class NotificationService {

    @Autowired private NotificationRepository notifRepo;
    @Autowired private UserRepository userRepo;

    /** 在线连接：userId -> emitters */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(3600_000L);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) emitters.remove(userId);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok-" + userId));
        } catch (Exception ignore) {
            remove.run();
        }
        return emitter;
    }

    /** 推送一条 SSE 事件（不落库） */
    public void pushEvent(Long userId, Map<String, Object> payload) {
        if (userId == null) return;
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("msg").data(payload));
            } catch (Exception ex) {
                list.remove(e);
            }
        }
        if (list.isEmpty()) emitters.remove(userId);
    }

    /** 站内消息：落库 + 实时推送 */
    public Notification notify(Long userId, String type, String title, String content, Long refId) {
        if (userId == null) return null;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setReadFlag(false);
        n.setCreatedAt(LocalDateTime.now());
        notifRepo.save(n);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", n.getId());
        p.put("type", type);
        p.put("title", title);
        p.put("content", content);
        p.put("refId", refId);
        p.put("createdAt", java.sql.Timestamp.valueOf(n.getCreatedAt()).getTime());
        pushEvent(userId, p);
        return n;
    }

    /** 广播 SSE 事件给所有在线用户（不落库，如新公告） */
    public void broadcast(Map<String, Object> payload) {
        for (Long uid : new ArrayList<>(emitters.keySet())) pushEvent(uid, payload);
    }

    public List<Map<String, Object>> list(Long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (userId == null) return out;
        for (Notification n : notifRepo.findTop30ByUserIdOrderByIdDesc(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("refId", n.getRefId());
            m.put("read", Boolean.TRUE.equals(n.getReadFlag()));
            m.put("createdAt", n.getCreatedAt() != null ? java.sql.Timestamp.valueOf(n.getCreatedAt()).getTime() : null);
            out.add(m);
        }
        return out;
    }

    public long unreadCount(Long userId) {
        if (userId == null) return 0;
        return notifRepo.countByUserIdAndReadFlagFalse(userId);
    }

    public void markRead(Long userId, Long id) {
        if (userId == null || id == null) return;
        notifRepo.findById(id).ifPresent(n -> {
            if (userId.equals(n.getUserId()) && !Boolean.TRUE.equals(n.getReadFlag())) {
                n.setReadFlag(true);
                notifRepo.save(n);
            }
        });
    }

    public void markAllRead(Long userId) {
        if (userId == null) return;
        List<Notification> list = notifRepo.findByUserIdAndReadFlagFalse(userId);
        for (Notification n : list) n.setReadFlag(true);
        if (!list.isEmpty()) notifRepo.saveAll(list);
    }

    /** 心跳：保持 SSE 连接活跃并清理失效连接 */
    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        for (Long uid : new ArrayList<>(emitters.keySet())) {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(uid);
            if (list == null) continue;
            for (SseEmitter e : list) {
                try { e.send(SseEmitter.event().comment("ping")); }
                catch (Exception ex) { list.remove(e); }
            }
            if (list.isEmpty()) emitters.remove(uid);
        }
    }
}
