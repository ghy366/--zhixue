package com.zhixue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.entity.GraduateAdvisor;
import com.zhixue.entity.GraduatePaper;
import com.zhixue.entity.GraduateAnnouncement;
import com.zhixue.entity.GraduateRequirement;

import com.zhixue.entity.GraduateMessage;
import com.zhixue.entity.User;
import com.zhixue.repository.GraduateAdvisorRepository;
import com.zhixue.repository.GraduatePaperRepository;
import com.zhixue.repository.GraduateRequirementRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraduateWorkspaceService {

    @Autowired private GraduateAdvisorRepository gaRepo;
    @Autowired private GraduateRequirementRepository reqRepo;
    @Autowired private GraduatePaperRepository paperRepo;
    @Autowired private com.zhixue.repository.GraduateMessageRepository msgRepo;
    @Autowired private com.zhixue.repository.GraduateAnnouncementRepository annoRepo;
    @Autowired private UserRepository userRepo;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${zhixue.upload.path:${user.home}/zhixue-uploads/papers}")
    private String uploadRoot;

    /** 列出某学生所有已确认的 advisorId */
    public List<GraduateAdvisor> studentApproved(Long studentId) {
        return gaRepo.findByStudentIdOrderByIdDesc(studentId).stream()
            .filter(g -> "approved".equals(g.getStatus())).collect(java.util.stream.Collectors.toList());
    }

    /** 列出某教师所有已确认的学生 */
    public List<GraduateAdvisor> teacherApproved(Long teacherId) {
        return gaRepo.findByTeacherIdAndStatusOrderByIdDesc(teacherId, "approved");
    }

    /** 组装工作台视图（一个 advisorId 的完整数据） */
    public Map<String, Object> workspace(Long advisorId, String role) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) return null;
        if (!"approved".equals(ga.getStatus())) return null;

        User student = userRepo.findById(ga.getStudentId()).orElse(null);
        User teacher = userRepo.findById(ga.getTeacherId()).orElse(null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("advisorId", advisorId);
        m.put("student", student == null ? null : userMap(student));
        m.put("teacher", teacher == null ? null : userMap(teacher));
        m.put("topic", ga.getTopic());
        m.put("approvedAt", ga.getUpdatedAt());

        GraduateRequirement latest = reqRepo.findFirstByAdvisorIdOrderByCreatedAtDesc(advisorId);
        m.put("latestRequirement", latest == null ? null : requirementMap(latest));

        m.put("papers", paperRepo.findByAdvisorIdOrderByVersionNoDesc(advisorId)
            .stream().map(this::paperMap).collect(Collectors.toList()));

        return m;
    }

    public List<Map<String, Object>> studentWorkspaces(Long studentId) {
        return studentApproved(studentId).stream()
            .map(ga -> workspace(ga.getId(), "student"))
            .filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<Map<String, Object>> teacherWorkspaces(Long teacherId) {
        return teacherApproved(teacherId).stream()
            .map(ga -> workspace(ga.getId(), "teacher"))
            .filter(Objects::nonNull).collect(Collectors.toList());
    }

    /** 教师发布要求 */
    public GraduateRequirement publishRequirement(Long advisorId, Long teacherId,
            String content, List<Map<String, String>> topics, String deadlineStr) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null || !"approved".equals(ga.getStatus()) || !ga.getTeacherId().equals(teacherId)) {
            throw new IllegalArgumentException("无权发布要求");
        }
        GraduateRequirement r = new GraduateRequirement();
        r.setAdvisorId(advisorId);
        r.setContent(content);
        try { r.setTopicsJson(topics == null ? null : om.writeValueAsString(topics)); }
        catch (Exception e) { throw new RuntimeException("topics JSON 解析失败"); }
        if (deadlineStr != null && !deadlineStr.isBlank()) {
            r.setDeadline(LocalDateTime.parse(deadlineStr));
        }
        r.setCreatedAt(LocalDateTime.now());
        return reqRepo.save(r);
    }

    /** 上传论文（学生 or 教师） */
    public GraduatePaper uploadPaper(Long advisorId, Long uploaderId, String uploaderRole,
            MultipartFile file, String title, String note, String abstractText) throws IOException {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null || !"approved".equals(ga.getStatus())) {
            throw new IllegalArgumentException("advisor 不存在或未确认");
        }
        // 权限：学生只能给自己传；教师只能给自己指导的学生传
        if ("student".equals(uploaderRole) && !ga.getStudentId().equals(uploaderId)) {
            throw new IllegalArgumentException("无权上传");
        }
        if ("teacher".equals(uploaderRole) && !ga.getTeacherId().equals(uploaderId)) {
            throw new IllegalArgumentException("无权上传");
        }

        // 文件校验
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        long maxBytes = 100L * 1024 * 1024; // 100 MB
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件超过 100MB");
        }
        String origName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        // 白名单扩展名
        String lower = origName.toLowerCase();
        boolean okExt = lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                      || lower.endsWith(".txt") || lower.endsWith(".zip") || lower.endsWith(".rar");
        if (!okExt) throw new IllegalArgumentException("仅支持 pdf/doc/docx/txt/zip/rar");

        // 生成路径：{uploadRoot}/{advisorId}/v{version}_{ts}_{origName}
        Integer maxVer = paperRepo.findMaxVersionByAdvisorId(advisorId);
        int nextVer = (maxVer == null ? 0 : maxVer) + 1;
        StringBuilder sb = new StringBuilder(origName.length());
        for (char c : origName.toCharArray()) {
            if (c == '\\' || c == '/' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') sb.append('_');
            else sb.append(c);
        }
        String safeName = sb.toString();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String relPath = advisorId + "/v" + nextVer + "_" + ts + "_" + safeName;
        Path absDir = Paths.get(uploadRoot, String.valueOf(advisorId));
        Files.createDirectories(absDir);
        Path absFile = absDir.resolve("v" + nextVer + "_" + ts + "_" + safeName);
        Files.copy(file.getInputStream(), absFile, StandardCopyOption.REPLACE_EXISTING);

        GraduatePaper p = new GraduatePaper();
        p.setAdvisorId(advisorId);
        p.setVersionNo(nextVer);
        p.setTitle(title == null || title.isBlank() ? safeName : title);
        p.setAbstractText(abstractText);
        p.setFileName(safeName);
        p.setFilePath(relPath);
        p.setFileSizeBytes(file.getSize());
        p.setUploadedBy(uploaderRole);
        p.setUploaderId(uploaderId);
        p.setNote(note);
        p.setReviewStatus("pending");
        p.setCreatedAt(LocalDateTime.now());
        return paperRepo.save(p);
    }

    /** 教师批阅 */
    public GraduatePaper reviewPaper(Long paperId, Long teacherId, String comment, String status, Integer grade) {
        GraduatePaper p = paperRepo.findById(paperId).orElse(null);
        if (p == null) throw new IllegalArgumentException("论文不存在");
        GraduateAdvisor ga = gaRepo.findById(p.getAdvisorId()).orElse(null);
        if (ga == null || !ga.getTeacherId().equals(teacherId)) throw new IllegalArgumentException("无权批阅");
        if (comment != null) p.setTeacherComment(comment);
        if (status != null && (status.equals("pending") || status.equals("revised") || status.equals("approved"))) {
            p.setReviewStatus(status);
        }
        if (grade != null && grade >= 0 && grade <= 100) p.setGrade(grade);
        p.setReviewedAt(LocalDateTime.now());
        return paperRepo.save(p);
    }

    /** 获取文件绝对路径 */
    public Path paperAbsPath(Long paperId, Long userId, String role) {
        GraduatePaper p = paperRepo.findById(paperId).orElse(null);
        if (p == null) return null;
        GraduateAdvisor ga = gaRepo.findById(p.getAdvisorId()).orElse(null);
        if (ga == null) return null;
        // 权限
        if ("student".equals(role) && !ga.getStudentId().equals(userId)) return null;
        if ("teacher".equals(role) && !ga.getTeacherId().equals(userId)) return null;
        if ("admin".equals(role)) { /* admin 总是可以 */ }
        return Paths.get(uploadRoot, p.getFilePath());
    }

    // ============ 辅助 ============
    private Map<String, Object> userMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("no", u.getNo());
        m.put("name", u.getName());
        m.put("role", u.getRole());
        m.put("college", u.getCollege());
        m.put("major", u.getMajor());
        
        return m;
    }

    private Map<String, Object> requirementMap(GraduateRequirement r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("advisorId", r.getAdvisorId());
        m.put("content", r.getContent());
        m.put("topics", parseTopics(r.getTopicsJson()));
        m.put("deadline", r.getDeadline());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private List<Map<String, String>> parseTopics(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return om.readValue(json, om.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private Map<String, Object> paperMap(GraduatePaper p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("advisorId", p.getAdvisorId());
        m.put("versionNo", p.getVersionNo());
        m.put("title", p.getTitle());
        m.put("abstractText", p.getAbstractText());
        m.put("fileName", p.getFileName());
        m.put("fileSizeBytes", p.getFileSizeBytes());
        m.put("uploadedBy", p.getUploadedBy());
        m.put("uploaderId", p.getUploaderId());
        m.put("note", p.getNote());
        m.put("reviewStatus", p.getReviewStatus());
        m.put("teacherComment", p.getTeacherComment());
        m.put("grade", p.getGrade());
        m.put("reviewedAt", p.getReviewedAt());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    // ============ 私信 / 联系老师 ============

    /** 查对话历史（按 advisorId，权限：学生=自己，教师=自己指导的学生） */
    public List<Map<String, Object>> conversation(Long advisorId, Long userId, String role) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) throw new IllegalArgumentException("对话不存在");
        if ("student".equals(role) && !ga.getStudentId().equals(userId))
            throw new IllegalArgumentException("无权查看此对话");
        if ("teacher".equals(role) && !ga.getTeacherId().equals(userId))
            throw new IllegalArgumentException("无权查看此对话");

        List<GraduateMessage> msgs = msgRepo.findByAdvisorIdOrderBySentAtAsc(advisorId);

        // 把发给当前用户的未读消息标记为已读
        LocalDateTime now = LocalDateTime.now();
        msgs.forEach(m -> {
            if (m.getReceiverId().equals(userId) && m.getReadAt() == null) {
                m.setReadAt(now);
            }
        });
        msgRepo.saveAll(msgs);

        // 预查所有涉及的用户姓名，避免 N+1
        Set<Long> userIds = new HashSet<>();
        msgs.forEach(m -> { userIds.add(m.getSenderId()); userIds.add(m.getReceiverId()); });
        Map<Long, String> nameMap = new HashMap<>();
        userRepo.findAllById(userIds).forEach(u -> nameMap.put(u.getId(), u.getName()));

        return msgs.stream().map(m -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.getId());
            mm.put("advisorId", m.getAdvisorId());
            mm.put("senderId", m.getSenderId());
            mm.put("senderRole", m.getSenderRole());
            mm.put("senderName", nameMap.getOrDefault(m.getSenderId(), ""));
            mm.put("receiverId", m.getReceiverId());
            mm.put("receiverName", nameMap.getOrDefault(m.getReceiverId(), ""));
            mm.put("content", m.getContent());
            mm.put("sentAt", m.getSentAt());
            mm.put("readAt", m.getReadAt());
            return mm;
        }).collect(Collectors.toList());
    }

    /** 发送消息 */
    public GraduateMessage sendMessage(Long advisorId, Long senderId, String senderRole, String content) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) throw new IllegalArgumentException("对话不存在");
        if (!"approved".equals(ga.getStatus())) throw new IllegalArgumentException("导师未确认");
        // 权限
        if ("student".equals(senderRole) && !ga.getStudentId().equals(senderId))
            throw new IllegalArgumentException("无权发送");
        if ("teacher".equals(senderRole) && !ga.getTeacherId().equals(senderId))
            throw new IllegalArgumentException("无权发送");
        if (content == null || content.isBlank() || content.length() > 2000)
            throw new IllegalArgumentException("消息内容不能为空且不超过 2000 字");

        GraduateMessage m = new GraduateMessage();
        m.setAdvisorId(advisorId);
        m.setSenderId(senderId);
        m.setSenderRole(senderRole);
        m.setReceiverId("student".equals(senderRole) ? ga.getTeacherId() : ga.getStudentId());
        m.setContent(content.trim());
        m.setSentAt(LocalDateTime.now());
        return msgRepo.save(m);
    }

    /** 未读消息数（用于小红点） */
        /** 未读消息数（用于小红点） */
    public Long unreadCount(Long userId, String role) {
        List<Long> advisorIds;
        if ("student".equals(role)) {
            advisorIds = gaRepo.findByStudentIdOrderByIdDesc(userId).stream()
                .filter(ga -> "approved".equals(ga.getStatus()))
                .map(GraduateAdvisor::getId).collect(Collectors.toList());
        } else if ("teacher".equals(role)) {
            advisorIds = gaRepo.findByTeacherIdAndStatusOrderByIdDesc(userId, "approved")
                .stream().map(GraduateAdvisor::getId).collect(Collectors.toList());
        } else {
            return 0L;
        }
        long total = 0;
        for (Long aid : advisorIds) {
            total += msgRepo.countByAdvisorIdAndReceiverIdAndReadAtIsNull(aid, userId);
        }
        return total;
    }

    // ===== 公告：老师发布 =====
    public GraduateAnnouncement publishAnnouncement(Long teacherId, String title, String content, String fileName, String filePath) {
        if ((title == null || title.isBlank()) && (content == null || content.isBlank()) && fileName == null) {
            throw new IllegalArgumentException("标题、内容、附件至少填一个");
        }
        GraduateAnnouncement a = new GraduateAnnouncement();
        a.setTeacherId(teacherId);
        a.setTitle(title != null ? title.trim() : "");
        a.setContent(content != null ? content.trim() : "");
        a.setCreatedAt(java.time.LocalDateTime.now());
        if (fileName != null && !fileName.isBlank()) {
            a.setFileName(fileName.trim());
            a.setFilePath(filePath);
        }
        return annoRepo.save(a);
    }

    // ===== 公告：查询（学生查自己导师的所有公告，老师查自己发的）=====
    public List<Map<String, Object>> listAnnouncements(Long userId, String role) {
        List<GraduateAnnouncement> annos;
        if ("teacher".equals(role)) {
            annos = annoRepo.findByTeacherIdOrderByCreatedAtDesc(userId);
        } else if ("student".equals(role)) {
            List<GraduateAdvisor> myAdvisors = gaRepo.findByStudentIdOrderByIdDesc(userId).stream().filter(g -> "approved".equals(g.getStatus())).collect(java.util.stream.Collectors.toList());
            if (myAdvisors.isEmpty()) return new java.util.ArrayList<>();
            java.util.Set<Long> teacherIds = new java.util.HashSet<>();
            myAdvisors.forEach(g -> teacherIds.add(g.getTeacherId()));
            annos = new java.util.ArrayList<>();
            annoRepo.findAll().forEach(a -> {
                if (teacherIds.contains(a.getTeacherId())) annos.add(a);
            });
            annos.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        } else {
            return new java.util.ArrayList<>();
        }

        java.util.Set<Long> tIds = new java.util.HashSet<>();
        annos.forEach(a -> tIds.add(a.getTeacherId()));
        java.util.Map<Long, String> nameMap = new java.util.HashMap<>();
        userRepo.findAllById(tIds).forEach(u -> nameMap.put(u.getId(), u.getName()));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (GraduateAnnouncement a : annos) {
            Map<String, Object> mm = new java.util.LinkedHashMap<>();
            mm.put("id", a.getId());
            mm.put("teacherId", a.getTeacherId());
            mm.put("teacherName", nameMap.getOrDefault(a.getTeacherId(), ""));
            mm.put("title", a.getTitle());
            mm.put("content", a.getContent());
            mm.put("fileName", a.getFileName());
            mm.put("createdAt", a.getCreatedAt());
            result.add(mm);
        }
        return result;
    }

}
