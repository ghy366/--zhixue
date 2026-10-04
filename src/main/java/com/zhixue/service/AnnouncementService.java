package com.zhixue.service;

import com.zhixue.entity.Announcement;
import com.zhixue.entity.AnnouncementRead;
import com.zhixue.entity.User;
import com.zhixue.repository.AnnouncementReadRepository;
import com.zhixue.repository.AnnouncementRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 公告业务：置顶 / 有效期 / 已读跟踪 */
@Service
public class AnnouncementService {

    @Autowired private AnnouncementRepository annRepo;
    @Autowired private AnnouncementReadRepository readRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private NotificationService notificationService;

    private Map<String, Object> row(Announcement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("content", a.getContent());
        m.put("type", a.getType());
        m.put("pinned", Boolean.TRUE.equals(a.getPinned()));
        m.put("publisherName", a.getPublisherName());
        m.put("createdAt", a.getCreatedAt() != null ? java.sql.Timestamp.valueOf(a.getCreatedAt()).getTime() : null);
        m.put("updatedAt", a.getUpdatedAt() != null ? java.sql.Timestamp.valueOf(a.getUpdatedAt()).getTime() : null);
        m.put("expireTime", a.getExpireTime() != null ? java.sql.Timestamp.valueOf(a.getExpireTime()).getTime() : null);
        m.put("expired", a.getExpireTime() != null && a.getExpireTime().isBefore(LocalDateTime.now()));
        return m;
    }

    private boolean visible(Announcement a) {
        return a.getExpireTime() == null || a.getExpireTime().isAfter(LocalDateTime.now());
    }

    /** 公告列表：admin=true 含过期公告并附带已读统计；viewerId 用于标记个人已读 */
    public List<Map<String, Object>> list(Integer limit, Long viewerId, boolean admin) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Announcement> all = annRepo.findAllByOrderByPinnedDescCreatedAtDesc();
        if (!admin) all = all.stream().filter(this::visible).toList();
        int n = (limit == null || limit <= 0) ? all.size() : Math.min(limit, all.size());
        long totalUsers = userRepo.count();
        for (int i = 0; i < n; i++) {
            Announcement a = all.get(i);
            Map<String, Object> m = row(a);
            if (viewerId != null) m.put("read", readRepo.existsByAnnouncementIdAndUserId(a.getId(), viewerId));
            if (admin) {
                m.put("readCount", readRepo.countByAnnouncementId(a.getId()));
                m.put("totalUsers", totalUsers);
            }
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> list(Integer limit) { return list(limit, null, false); }

    public long count() { return annRepo.count(); }

    /** 未读且未过期的公告数 */
    public long unreadCount(Long userId) {
        if (userId == null) return 0;
        long unread = 0;
        for (Announcement a : annRepo.findAll()) {
            if (!visible(a)) continue;
            if (!readRepo.existsByAnnouncementIdAndUserId(a.getId(), userId)) unread++;
        }
        return unread;
    }

    @Transactional
    public void markRead(Long userId, Long announcementId) {
        if (userId == null || announcementId == null) return;
        if (readRepo.existsByAnnouncementIdAndUserId(announcementId, userId)) return;
        AnnouncementRead r = new AnnouncementRead();
        r.setAnnouncementId(announcementId);
        r.setUserId(userId);
        r.setReadAt(LocalDateTime.now());
        readRepo.save(r);
    }

    @Transactional
    public void markAllRead(Long userId) {
        if (userId == null) return;
        for (Announcement a : annRepo.findAll()) {
            if (!visible(a)) continue;
            markRead(userId, a.getId());
        }
    }

    @Transactional
    public Map<String, Object> create(Long publisherId, String title, String content, String type,
                                      Boolean pinned, LocalDateTime expireTime) {
        Map<String, Object> r = new HashMap<>();
        if (title == null || title.trim().isEmpty()) { r.put("success", false); r.put("msg", "请填写公告标题"); return r; }
        Announcement a = new Announcement();
        a.setTitle(title.trim());
        a.setContent(content);
        a.setType(type == null || type.isEmpty() ? "系统公告" : type);
        a.setPinned(Boolean.TRUE.equals(pinned));
        a.setPublisherId(publisherId);
        User u = publisherId == null ? null : userRepo.findById(publisherId).orElse(null);
        a.setPublisherName(u != null ? u.getName() : "管理员");
        a.setCreatedAt(LocalDateTime.now());
        a.setExpireTime(expireTime);
        annRepo.save(a);
        // 实时广播给在线用户（通知中心红点由已读表统计，广播不落库）
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "announcement");
        p.put("title", "📢 新公告：" + a.getTitle());
        p.put("content", a.getContent() == null ? "" : (a.getContent().length() > 60 ? a.getContent().substring(0, 60) + "…" : a.getContent()));
        p.put("refId", a.getId());
        p.put("createdAt", java.sql.Timestamp.valueOf(a.getCreatedAt()).getTime());
        try { notificationService.broadcast(p); } catch (Exception ignore) {}
        r.put("success", true);
        r.put("msg", "公告已发布");
        r.put("id", a.getId());
        return r;
    }

    @Transactional
    public Map<String, Object> update(Long id, String title, String content, String type, Boolean pinned,
                                      LocalDateTime expireTime, boolean expireSet) {
        Map<String, Object> r = new HashMap<>();
        Announcement a = annRepo.findById(id).orElse(null);
        if (a == null) { r.put("success", false); r.put("msg", "公告不存在"); return r; }
        if (title != null && !title.trim().isEmpty()) a.setTitle(title.trim());
        if (content != null) a.setContent(content);
        if (type != null && !type.isEmpty()) a.setType(type);
        if (pinned != null) a.setPinned(pinned);
        if (expireSet) a.setExpireTime(expireTime);
        a.setUpdatedAt(LocalDateTime.now());
        annRepo.save(a);
        r.put("success", true);
        r.put("msg", "公告已更新");
        return r;
    }

    @Transactional
    public Map<String, Object> delete(Long id) {
        Map<String, Object> r = new HashMap<>();
        if (!annRepo.existsById(id)) { r.put("success", false); r.put("msg", "公告不存在"); return r; }
        annRepo.deleteById(id);
        readRepo.deleteByAnnouncementId(id);
        r.put("success", true);
        r.put("msg", "公告已删除");
        return r;
    }
}
