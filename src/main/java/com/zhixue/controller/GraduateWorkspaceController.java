package com.zhixue.controller;



import com.zhixue.dto.ApiResponse;

import com.zhixue.entity.User;
import com.zhixue.entity.GraduateAnnouncement;

import com.zhixue.service.AuthService;
import com.zhixue.util.SessionStore;

import com.zhixue.service.GraduateWorkspaceService;
import com.zhixue.repository.GraduateAdvisorRepository;
import com.zhixue.repository.GraduateAnnouncementRepository;
import java.util.HashSet;
import com.zhixue.repository.GraduateMessageRepository;
import com.zhixue.entity.GraduateMessage;
import com.zhixue.entity.GraduateAdvisor;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.core.io.Resource;

import org.springframework.core.io.UrlResource;

import org.springframework.http.HttpHeaders;

import org.springframework.http.MediaType;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;



import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.nio.file.Path;

import java.util.*;
import java.util.LinkedHashMap;



@RestController

@RequestMapping("/api/workspace")

public class GraduateWorkspaceController {



    @Autowired private GraduateWorkspaceService ws;
    @Autowired private com.zhixue.repository.GraduateAnnouncementRepository annoRepo;
    @Autowired private com.zhixue.repository.GraduateAdvisorRepository gaRepo;
    @Autowired private com.zhixue.repository.GraduateMessageRepository msgRepo;

    @Autowired private AuthService auth;



    @Autowired private com.zhixue.repository.UserRepository userRepo;
    private User me(HttpServletRequest req) {
        String token = req.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        Long uid = SessionStore.getUserId(token);
        return uid == null ? null : userRepo.findById(uid).orElse(null);
    }



    // ===== 学生 =====

    @GetMapping("/my")

    public ApiResponse<?> my(HttpServletRequest req) {

        User u = me(req);

        if (u == null) return ApiResponse.fail("未登录");

        if (!"student".equals(u.getRole())) return ApiResponse.fail("仅学生");

        ws.syncAdvisorFromCourses(u.getId());   // 课程教师 → 毕设导师 同步（幂等）

        return ApiResponse.ok(ws.studentWorkspaces(u.getId()));

    }



    // ===== 教师 =====

    @GetMapping("/teacher/students")

    public ApiResponse<?> teacherList(HttpServletRequest req) {

        User u = me(req);

        if (u == null) return ApiResponse.fail("未登录");

        if (!"teacher".equals(u.getRole())) return ApiResponse.fail("仅教师");

        return ApiResponse.ok(ws.teacherWorkspaces(u.getId()));

    }



    // 通用：按 advisorId 查工作台详情（角色权限在 service 内通过 user.getId + ga 关系校验)

    @GetMapping("/advisor/{advisorId}")

    public ApiResponse<?> advisor(@PathVariable Long advisorId, HttpServletRequest req) {

        User u = me(req);

        if (u == null) return ApiResponse.fail("未登录");

        Map<String, Object> data = ws.workspace(advisorId, u.getRole());

        if (data == null) return ApiResponse.fail("工作台不存在或无权查看");

        return ApiResponse.ok(data);

    }



    // ===== 学生确认课题 =====
    @PostMapping("/topic")
    public ApiResponse<?> setTopic(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        if (!"student".equals(u.getRole())) return ApiResponse.fail("仅学生");
        Long advisorId = Long.valueOf(body.get("advisorId").toString());
        String title = (String) body.get("title");
        try {
            return ApiResponse.ok(ws.confirmTopic(advisorId, u.getId(), title));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    // ===== 教师发布要求 =====

    @PostMapping("/requirements")

    public ApiResponse<?> publishReq(@RequestBody Map<String, Object> body, HttpServletRequest req) {

        User u = me(req);

        if (u == null) return ApiResponse.fail("未登录");

        if (!"teacher".equals(u.getRole())) return ApiResponse.fail("仅教师");

        Long advisorId = Long.valueOf(body.get("advisorId").toString());

        String content = (String) body.get("content");

        @SuppressWarnings("unchecked")

        List<Map<String, String>> topics = (List) body.get("topics");

        String deadline = (String) body.get("deadline");

        try {

            return ApiResponse.ok(ws.publishRequirement(advisorId, u.getId(), content, topics, deadline));

        } catch (Exception e) {

            return ApiResponse.fail(e.getMessage());

        }

    }



    // ===== 上传论文（学生 or 教师，multipart） =====

    @PostMapping("/papers/upload")

    public ApiResponse<?> upload(

            @RequestParam Long advisorId,

            @RequestParam(required = false) String title,

            @RequestParam(required = false) String note,

            @RequestParam(required = false) String abstractText,

            @RequestParam("file") MultipartFile file,

            HttpServletRequest req) {

        User u = me(req);

        if (u == null) return ApiResponse.fail("未登录");

        String role = u.getRole();

        if (!"student".equals(role) && !"teacher".equals(role)) return ApiResponse.fail("仅学生/教师可上传");

        try {

            return ApiResponse.ok(ws.uploadPaper(advisorId, u.getId(), role, file, title, note, abstractText));

        } catch (Exception e) {

            return ApiResponse.fail(e.getMessage());

        }

    }



    // ===== 教师批阅 =====

    @PostMapping("/papers/{paperId}/review")

    public ApiResponse<?> review(@PathVariable Long paperId,

            @RequestBody Map<String, Object> body, HttpServletRequest req) {

        User u = me(req);

        if (u == null) return ApiResponse.fail("未登录");

        if (!"teacher".equals(u.getRole())) return ApiResponse.fail("仅教师");

        String comment = (String) body.get("comment");

        String status = (String) body.get("status");

        Integer grade = body.get("grade") == null ? null : Integer.valueOf(body.get("grade").toString());

        try {

            return ApiResponse.ok(ws.reviewPaper(paperId, u.getId(), comment, status, grade));

        } catch (Exception e) {

            return ApiResponse.fail(e.getMessage());

        }

    }



    // ===== 下载（所有角色通过权限校验后下载） =====

    @GetMapping("/papers/{paperId}/download")

    public ResponseEntity<Resource> download(@PathVariable Long paperId, HttpServletRequest req) {

        User u = me(req);

        if (u == null) return ResponseEntity.status(401).build();

        Path p = ws.paperAbsPath(paperId, u.getId(), u.getRole());

        if (p == null) return ResponseEntity.status(404).build();

        try {

            if (!p.toFile().exists()) return ResponseEntity.status(404).build();

            Resource res = new UrlResource(p.toUri());

            String encoded = URLEncoder.encode(p.getFileName().toString(), StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + java.net.URLEncoder.encode(p.getFileName().toString(), "UTF-8").replace("+", "%20"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }


    

    // ===== 流程里程碑：开题报告 → 中期检查 → 论文终稿 → 答辩 =====

    /** 查询流程节点（学生本人 / 指导教师） */
    @GetMapping("/milestones")
    public ApiResponse<?> milestones(@RequestParam Long advisorId, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        try {
            return ApiResponse.ok(ws.milestones(advisorId, u.getId(), u.getRole()));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 学生提交节点材料（multipart，文件可选） */
    @PostMapping(value = "/milestones/{id}/submit", consumes = "multipart/form-data")
    public ApiResponse<?> submitMilestone(@PathVariable Long id,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) MultipartFile file,
            HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        if (!"student".equals(u.getRole())) return ApiResponse.fail("仅学生可提交流程材料");
        try {
            return ApiResponse.ok("已提交，等待导师审核", ws.submitMilestone(id, u.getId(), file, title, content));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 导师审核节点（passed/returned；答辩节点通过须带 advisorScore + defenseScore） */
    @PostMapping("/milestones/{id}/review")
    public ApiResponse<?> reviewMilestone(@PathVariable Long id,
            @RequestBody Map<String, Object> body, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        if (!"teacher".equals(u.getRole())) return ApiResponse.fail("仅教师可审核");
        String decision = (String) body.get("decision");
        String comment = (String) body.get("comment");
        Integer advisorScore = body.get("advisorScore") == null ? null : Integer.valueOf(body.get("advisorScore").toString());
        Integer defenseScore = body.get("defenseScore") == null ? null : Integer.valueOf(body.get("defenseScore").toString());
        try {
            return ApiResponse.ok("passed".equals(decision) ? "已通过" : "已退回",
                    ws.reviewMilestone(id, u.getId(), decision, comment, advisorScore, defenseScore));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 里程碑材料下载 */
    @GetMapping("/milestones/{id}/download")
    public ResponseEntity<Resource> downloadMilestone(@PathVariable Long id, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ResponseEntity.status(401).build();
        Path p = ws.milestoneAbsPath(id, u.getId(), u.getRole());
        if (p == null || !p.toFile().exists()) return ResponseEntity.status(404).build();
        try {
            Resource res = new UrlResource(p.toUri());
            String encoded = URLEncoder.encode(p.getFileName().toString(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // ===== 聊天：查看对话（学生 + 老师都能用）=====
    @GetMapping("/chat")
    public ApiResponse<?> chatList(@RequestParam Long advisorId, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        try {
            GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
            if (ga == null) return ApiResponse.fail("对话不存在");
            if ("teacher".equals(u.getRole()) && !ga.getTeacherId().equals(u.getId())) return ApiResponse.fail("无权查看");
            if ("student".equals(u.getRole()) && !ga.getStudentId().equals(u.getId())) return ApiResponse.fail("无权查看");
            List<GraduateMessage> msgs = msgRepo.findByAdvisorIdOrderBySentAtAsc(advisorId);
            Set<Long> userIds = new HashSet<>();
            msgs.forEach(m -> { userIds.add(m.getSenderId()); userIds.add(m.getReceiverId()); });
            Map<Long, String> nameMap = new HashMap<>();
            userRepo.findAllById(userIds).forEach(uu -> nameMap.put(uu.getId(), uu.getName()));
            List<Map<String, Object>> result = new ArrayList<>();
            for (GraduateMessage m : msgs) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("id", m.getId());
                mm.put("advisorId", m.getAdvisorId());
                mm.put("senderId", m.getSenderId());
                mm.put("senderRole", m.getSenderRole());
                mm.put("senderName", nameMap.getOrDefault(m.getSenderId(), ""));
                mm.put("content", m.getContent());
                mm.put("sentAt", m.getSentAt());
                result.add(mm);
            }
            // 标记已读
            try { msgRepo.markReadByAdvisorAndReceiver(advisorId, u.getId()); } catch (Exception ignored) {}
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    // ===== 聊天：发送消息 =====
    @PostMapping("/chat")
    public ApiResponse<?> chatSend(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        Long advisorId = Long.valueOf(body.get("advisorId").toString());
        String content = (String) body.get("content");
        if (content == null || content.isBlank()) return ApiResponse.fail("内容不能为空");
        try {
            GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
            if (ga == null) return ApiResponse.fail("对话不存在");
            if ("teacher".equals(u.getRole()) && !ga.getTeacherId().equals(u.getId())) return ApiResponse.fail("无权发送");
            if ("student".equals(u.getRole()) && !ga.getStudentId().equals(u.getId())) return ApiResponse.fail("无权发送");
            GraduateMessage msg = new GraduateMessage();
            msg.setAdvisorId(advisorId);
            msg.setSenderId(u.getId());
            msg.setSenderRole(u.getRole());
            msg.setReceiverId("teacher".equals(u.getRole()) ? ga.getStudentId() : ga.getTeacherId());
            msg.setContent(content.trim());
            msg.setSentAt(java.time.LocalDateTime.now());
            msgRepo.save(msg);
            return ApiResponse.ok(msg);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    // ===== 公告：发布（老师）=====
    @PostMapping("/announce")
    public ApiResponse<?> announce(@RequestBody java.util.Map<String, Object> body, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        if (!"teacher".equals(u.getRole())) return ApiResponse.fail("仅教师可发布公告");
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String fileName = (String) body.get("fileName");
        String filePath = (String) body.get("filePath");
        try {
            GraduateAnnouncement a = ws.publishAnnouncement(u.getId(), title, content, fileName, filePath);
            return ApiResponse.ok(a);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    // ===== 公告：列表 =====
    @GetMapping("/announcements")
    public ApiResponse<?> announcements(HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        try {
            return ApiResponse.ok(ws.listAnnouncements(u.getId(), u.getRole()));
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

// ===== 公告文件上传（老师先上传文件拿到路径）=====
    @PostMapping(value = "/announce/upload", consumes = "multipart/form-data")
    public ApiResponse<?> uploadAnnoFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file, HttpServletRequest req) {
        User u = me(req);
        if (u == null) return ApiResponse.fail("未登录");
        if (!"teacher".equals(u.getRole())) return ApiResponse.fail("仅教师可上传");
        if (file == null || file.isEmpty()) return ApiResponse.fail("请选择文件");
        try {
            String orig = file.getOriginalFilename();
            String ext = orig != null && orig.contains(".") ? orig.substring(orig.lastIndexOf(".")) : "";
            String safeName = "anno_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0,6) + ext;
            java.io.File dir = new java.io.File(System.getProperty("user.home") + "/zhixue-uploads/announcements");
            if (!dir.exists()) dir.mkdirs();
            java.io.File dest = new java.io.File(dir, safeName);
            file.transferTo(dest);
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("fileName", orig);
            r.put("filePath", dest.getAbsolutePath());
            return ApiResponse.ok(r);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    // ===== 公告文件下载（权限同聊天/文件隔离）=====
    @GetMapping("/announce/file/{id}")
    public ResponseEntity<?> downloadAnnoFile(@PathVariable Long id, HttpServletRequest req) {
        try {
            User u = me(req);
            if (u == null) return ResponseEntity.status(401).build();
            GraduateAnnouncement a = annoRepo.findById(id).orElse(null);
            if (a == null || a.getFilePath() == null) return ResponseEntity.notFound().build();
            if ("student".equals(u.getRole())) {
                List<com.zhixue.entity.GraduateAdvisor> myAdvisors = gaRepo.findByStudentIdOrderByIdDesc(u.getId());
                boolean ok = myAdvisors.stream().anyMatch(g -> "approved".equals(g.getStatus()) && g.getTeacherId().equals(a.getTeacherId()));
                if (!ok) return ResponseEntity.status(403).build();
            } else if ("teacher".equals(u.getRole()) && !a.getTeacherId().equals(u.getId())) {
                return ResponseEntity.status(403).build();
            }
            java.io.File f = new java.io.File(a.getFilePath());
            if (!f.exists()) return ResponseEntity.notFound().build();
            org.springframework.core.io.Resource res = new org.springframework.core.io.UrlResource(f.toURI());
            String encoded = java.net.URLEncoder.encode(a.getFileName(), "UTF-8").replace("+", "%20");
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encoded)
                .body(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

}
