package com.zhixue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.entity.GraduateAdvisor;
import com.zhixue.entity.GraduatePaper;
import com.zhixue.entity.GraduateAnnouncement;
import com.zhixue.entity.GraduateRequirement;

import com.zhixue.entity.GraduateMessage;
import com.zhixue.entity.GraduateMilestone;
import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.GraduateAdvisorRepository;
import com.zhixue.repository.GraduatePaperRepository;
import com.zhixue.repository.GraduateRequirementRepository;
import com.zhixue.repository.GraduateMilestoneRepository;
import com.zhixue.repository.GraduateGuidanceRecordRepository;
import com.zhixue.repository.GraduatePlagiarismRepository;
import com.zhixue.entity.GraduatePlagiarism;
import com.zhixue.entity.GraduateGuidanceRecord;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    @Autowired private GraduateMilestoneRepository msRepo;
    @Autowired private GraduateGuidanceRecordRepository gRecRepo;
    @Autowired private GraduatePlagiarismRepository plagRepo;

    /** 查重重复率上限（%）：超过只能退回，达标才放行答辩资格 */
    public static final double PLAGIARISM_MAX = 30.0;
    @Autowired private UserRepository userRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${zhixue.upload.path:E:/ai文件 图片缓存/papers}")
    private String uploadRoot;

    /** 列出某学生所有已确认的 advisorId */
    public List<GraduateAdvisor> studentApproved(Long studentId) {
        return gaRepo.findByStudentIdOrderByIdDesc(studentId).stream()
            .filter(g -> "approved".equals(g.getStatus())).collect(java.util.stream.Collectors.toList());
    }

    /** 学生确认课题：从导师推荐题目中选定，或自定义题目（仅限本人、导师已确认的 advisorship） */
    @Transactional
    public Map<String, Object> confirmTopic(Long advisorId, Long studentId, String title) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null || !ga.getStudentId().equals(studentId)) {
            throw new RuntimeException("工作台不存在或无权操作");
        }
        if (!"approved".equals(ga.getStatus())) {
            throw new RuntimeException("导师确认申请后才能确定课题");
        }
        if (title == null || title.isBlank()) {
            throw new RuntimeException("题目不能为空");
        }
        ga.setTopic(title.trim());
        ga.setUpdatedAt(LocalDateTime.now());
        gaRepo.save(ga);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("topic", ga.getTopic());
        r.put("msg", "课题已确认：" + ga.getTopic());
        return r;
    }

    /** 列出某教师所有已确认的学生 */
    public List<GraduateAdvisor> teacherApproved(Long teacherId) {
        return gaRepo.findByTeacherIdAndStatusOrderByIdDesc(teacherId, "approved");
    }

    /** 课程教师 → 毕设导师同步：学生被录取进「毕业设计(上)/(下)」后，
     *  授课教师（优先 (上)，开题环节主带）自动成为其毕业设计导师。
     *  advisorId 保持不变——已上传的文件与流程节点全部保留；幂等，重复调用无副作用。
     *  学生端工作台加载时触发（懒同步）。 */
    @Transactional
    public void syncAdvisorFromCourses(Long studentId) {
        List<Enrollment> enrolls = enrollmentRepo.findByStudentId(studentId).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .collect(Collectors.toList());
        Long targetTeacher = null;
        String courseName = null;
        for (Enrollment e : enrolls) {
            Course c = courseRepo.findById(e.getCourseId()).orElse(null);
            if (c == null || c.getName() == null || !c.getName().startsWith("毕业设计")
                    || c.getTeacherId() == null) continue;
            /* 优先「毕业设计(上)」：开题环节由该教师主带 */
            if (c.getName().contains("(上)") || c.getName().contains("（上）")) {
                targetTeacher = c.getTeacherId(); courseName = c.getName(); break;
            }
            if (targetTeacher == null) { targetTeacher = c.getTeacherId(); courseName = c.getName(); }
        }
        if (targetTeacher == null) return;

        java.util.List<GraduateAdvisor> approved = studentApproved(studentId);
        if (approved.isEmpty()) {
            GraduateAdvisor ga = new GraduateAdvisor();
            ga.setStudentId(studentId);
            ga.setTeacherId(targetTeacher);
            ga.setStatus("approved");
            ga.setTopic(courseName);
            ga.setCreatedAt(LocalDateTime.now());
            ga.setUpdatedAt(LocalDateTime.now());
            ga = gaRepo.save(ga);
            ensureMilestones(ga.getId());
            return;
        }
        GraduateAdvisor ga = approved.get(0);              // 多条时取最新一条
        if (targetTeacher.equals(ga.getTeacherId())) return;   // 已同步，幂等
        ga.setTeacherId(targetTeacher);                    // 课程教师为准；advisorId 不变，文件与流程全保留
        ga.setUpdatedAt(LocalDateTime.now());
        gaRepo.save(ga);
        ensureMilestones(ga.getId());
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

    // ============ 流程里程碑：开题报告 → 中期检查 → 论文终稿 → 答辩 ============

    /** 固定串行阶段顺序 */
    private static final List<String> STAGES = List.of("opening", "midterm", "final", "defense");

    /** 懒初始化：已确认的导师关系若无里程碑记录则补齐 4 条（含历史数据兼容） */
    public void ensureMilestones(Long advisorId) {
        List<GraduateMilestone> exist = msRepo.findByAdvisorId(advisorId);
        if (exist.size() >= STAGES.size()) return;
        Set<String> has = exist.stream().map(GraduateMilestone::getStage).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (String st : STAGES) {
            if (has.contains(st)) continue;
            GraduateMilestone m = new GraduateMilestone();
            m.setAdvisorId(advisorId);
            m.setStage(st);
            m.setStatus("none");
            m.setCreatedAt(now);
            msRepo.save(m);
        }
    }

    /** 按学生/导师身份查询流程节点（含 canSubmit/canReview 操作标记与串行解锁状态） */
    public List<Map<String, Object>> milestones(Long advisorId, Long userId, String role) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) throw new IllegalArgumentException("工作台不存在");
        if ("student".equals(role) && !ga.getStudentId().equals(userId)) throw new IllegalArgumentException("无权查看");
        if ("teacher".equals(role) && !ga.getTeacherId().equals(userId)) throw new IllegalArgumentException("无权查看");
        if (!"approved".equals(ga.getStatus())) throw new IllegalArgumentException("导师关系未确认，暂无流程节点");
        ensureMilestones(advisorId);

        List<GraduateMilestone> all = msRepo.findByAdvisorId(advisorId);
        List<GraduateMilestone> ordered = new ArrayList<>();
        for (String st : STAGES) {
            all.stream().filter(m -> st.equals(m.getStage())).findFirst().ifPresent(ordered::add);
        }
        // 查重登记（终稿通过后解锁）：达标才放行答辩环节
        GraduatePlagiarism plag = ensurePlagiarism(advisorId);
        boolean plagPassed = "passed".equals(plag.getStatus());
        List<Map<String, Object>> out = new ArrayList<>();
        String prevStatus = "passed"; // 第一节点始终可提交
        for (GraduateMilestone m : ordered) {
            Map<String, Object> mm = milestoneMap(m);
            boolean unlocked = "passed".equals(prevStatus);
            boolean canSubmit = "student".equals(role) && unlocked
                    && ("none".equals(m.getStatus()) || "returned".equals(m.getStatus()));
            if ("defense".equals(m.getStage()) && !plagPassed) {
                canSubmit = false;   // 查重未达标：答辩环节锁定
                mm.put("lockedByPlagiarism", true);
            }
            mm.put("unlocked", unlocked);
            mm.put("canSubmit", canSubmit);
            mm.put("canReview", "teacher".equals(role) && "submitted".equals(m.getStatus()));
            out.add(mm);
            prevStatus = m.getStatus();
        }
        return out;
    }

    /** 学生提交节点材料（文件可选，标题/说明至少一项） */
    public GraduateMilestone submitMilestone(Long id, Long studentId, MultipartFile file,
            String title, String content) throws IOException {
        GraduateMilestone m = msRepo.findById(id).orElse(null);
        if (m == null) throw new IllegalArgumentException("流程节点不存在");
        GraduateAdvisor ga = gaRepo.findById(m.getAdvisorId()).orElse(null);
        if (ga == null || !ga.getStudentId().equals(studentId)) throw new IllegalArgumentException("无权提交");
        if (!"approved".equals(ga.getStatus())) throw new IllegalArgumentException("导师关系未确认");
        if (!"none".equals(m.getStatus()) && !"returned".equals(m.getStatus())) {
            throw new IllegalArgumentException("该节点当前不可提交（待审核或已通过）");
        }
        // 串行校验：前置节点须全部通过
        List<GraduateMilestone> all = msRepo.findByAdvisorId(m.getAdvisorId());
        int idx = STAGES.indexOf(m.getStage());
        for (int i = 0; i < idx; i++) {
            final String st = STAGES.get(i);
            GraduateMilestone prev = all.stream().filter(x -> st.equals(x.getStage())).findFirst().orElse(null);
            if (prev == null || !"passed".equals(prev.getStatus())) {
                throw new IllegalArgumentException("前一道环节尚未通过，请先完成「" + stageName(STAGES.get(i)) + "」");
            }
        }
        boolean hasFile = file != null && !file.isEmpty();
        if ((title == null || title.isBlank()) && (content == null || content.isBlank()) && !hasFile) {
            throw new IllegalArgumentException("标题、说明与材料文件至少填写/上传一项");
        }
        if (hasFile) {
            long maxBytes = 100L * 1024 * 1024;
            if (file.getSize() > maxBytes) throw new IllegalArgumentException("文件超过 100MB");
            String origName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String lower = origName.toLowerCase();
            boolean okExt = lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                          || lower.endsWith(".txt") || lower.endsWith(".zip") || lower.endsWith(".rar")
                          || lower.endsWith(".ppt") || lower.endsWith(".pptx");
            if (!okExt) throw new IllegalArgumentException("仅支持 pdf/doc/docx/txt/zip/rar/ppt/pptx");
            StringBuilder sb = new StringBuilder(origName.length());
            for (char c : origName.toCharArray()) {
                if ("\\/:*?\"<>|".indexOf(c) >= 0) sb.append('_'); else sb.append(c);
            }
            String safeName = sb.toString();
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String relPath = "milestones/" + m.getAdvisorId() + "/" + m.getStage() + "_" + ts + "_" + safeName;
            Path absDir = Paths.get(uploadRoot, "milestones", String.valueOf(m.getAdvisorId()));
            Files.createDirectories(absDir);
            Files.copy(file.getInputStream(), absDir.resolve(m.getStage() + "_" + ts + "_" + safeName),
                    StandardCopyOption.REPLACE_EXISTING);
            m.setFileName(safeName);
            m.setFilePath(relPath);
            m.setFileSizeBytes(file.getSize());
        }
        if (title != null && !title.isBlank()) m.setTitle(title.trim());
        if (content != null && !content.isBlank()) m.setContent(content.trim());
        m.setStatus("submitted");
        m.setSubmittedAt(LocalDateTime.now());
        return msRepo.save(m);
    }

    /**
     * 导师审核节点；decision=passed/returned。
     * 答辩节点通过须三方分数齐全：导师评分 + 评阅教师评分（教务指派的交叉评阅已录入）+ 答辩组评分，
     * 总评 = 导师×40% + 评阅×20% + 答辩×40%（对齐真实高校毕设三方评分链）。
     */
    public GraduateMilestone reviewMilestone(Long id, Long teacherId, String decision, String comment,
            Integer advisorScore, Integer defenseScore) {
        GraduateMilestone m = msRepo.findById(id).orElse(null);
        if (m == null) throw new IllegalArgumentException("流程节点不存在");
        GraduateAdvisor ga = gaRepo.findById(m.getAdvisorId()).orElse(null);
        if (ga == null || !ga.getTeacherId().equals(teacherId)) throw new IllegalArgumentException("无权审核");
        if (!"submitted".equals(m.getStatus())) throw new IllegalArgumentException("该节点没有待审核的提交");
        if ("passed".equals(decision)) {
            if ("defense".equals(m.getStage())) {
                if (advisorScore == null || defenseScore == null
                        || advisorScore < 0 || advisorScore > 100 || defenseScore < 0 || defenseScore > 100) {
                    throw new IllegalArgumentException("答辩通过须录入 0-100 的导师评分与答辩组评分");
                }
                if (m.getReviewerScore() == null) {
                    throw new IllegalArgumentException("评阅教师尚未评分，暂不能通过答辩节点（三方评分链：导师40% + 评阅20% + 答辩40%）");
                }
                GraduatePlagiarism plag = plagRepo.findByAdvisorId(m.getAdvisorId()).orElse(null);
                if (plag == null || !"passed".equals(plag.getStatus())) {
                    throw new IllegalArgumentException("查重未达标，暂不能通过答辩节点（先完成查重登记并通过审核）");
                }
                m.setAdvisorScore(advisorScore);
                m.setDefenseScore(defenseScore);
                m.setTotalScore((int) Math.round(advisorScore * 0.4 + m.getReviewerScore() * 0.2 + defenseScore * 0.4));
            }
            m.setStatus("passed");
        } else if ("returned".equals(decision)) {
            m.setStatus("returned");
        } else {
            throw new IllegalArgumentException("decision 必须是 passed 或 returned");
        }
        if (comment != null) m.setTeacherComment(comment.trim());
        m.setReviewedAt(LocalDateTime.now());
        return msRepo.save(m);
    }

    // ============ 评阅教师（三方评分链：教务指派交叉评阅） ============

    /** 教务指派评阅教师：中期已通过才可指派，且不能是导师本人 */
    public Map<String, Object> assignReviewer(Long advisorId, Long reviewerId) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) throw new IllegalArgumentException("双选关系不存在");
        if (!"approved".equals(ga.getStatus())) throw new IllegalArgumentException("导师关系未确认");
        User reviewer = userRepo.findById(reviewerId).orElse(null);
        if (reviewer == null || !"teacher".equals(reviewer.getRole())) throw new IllegalArgumentException("评阅教师必须是教师账号");
        if (reviewer.getId().equals(ga.getTeacherId())) throw new IllegalArgumentException("评阅教师不能是导师本人");
        // 中期已通过才进入评阅阶段
        boolean midtermPassed = msRepo.findByAdvisorId(advisorId).stream()
                .anyMatch(m -> "midterm".equals(m.getStage()) && "passed".equals(m.getStatus()));
        if (!midtermPassed) throw new IllegalArgumentException("中期检查通过后才能指派评阅教师");
        GraduateMilestone defense = msRepo.findByAdvisorId(advisorId).stream()
                .filter(m -> "defense".equals(m.getStage())).findFirst().orElse(null);
        if (defense == null) throw new IllegalArgumentException("流程节点异常：缺答辩节点");
        defense.setReviewerId(reviewer.getId());
        defense.setReviewerName(reviewer.getName());
        // 换人时清空旧评分
        defense.setReviewerScore(null);
        defense.setReviewerComment(null);
        defense.setReviewerAt(null);
        msRepo.save(defense);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("advisorId", advisorId);
        r.put("reviewerId", reviewer.getId());
        r.put("reviewerName", reviewer.getName());
        r.put("msg", "已指派 " + reviewer.getName() + " 为评阅教师");
        return r;
    }

    /** 评阅教师的待评阅任务：我作为评阅教师、中期已通过的任务列表 */
    public List<Map<String, Object>> myReviewTasks(Long reviewerId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GraduateMilestone m : msRepo.findAll()) {
            if (!"defense".equals(m.getStage()) || !reviewerId.equals(m.getReviewerId())) continue;
            GraduateAdvisor ga = gaRepo.findById(m.getAdvisorId()).orElse(null);
            if (ga == null) continue;
            boolean midtermPassed = msRepo.findByAdvisorId(m.getAdvisorId()).stream()
                    .anyMatch(x -> "midterm".equals(x.getStage()) && "passed".equals(x.getStatus()));
            if (!midtermPassed) continue;
            User stu = userRepo.findById(ga.getStudentId()).orElse(null);
            User tea = userRepo.findById(ga.getTeacherId()).orElse(null);
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("advisorId", m.getAdvisorId());
            t.put("studentName", stu != null ? stu.getName() : "未知");
            t.put("studentNo", stu != null ? stu.getNo() : "");
            t.put("studentMajor", stu != null ? stu.getMajor() : "");
            t.put("advisorName", tea != null ? tea.getName() : "未知");
            t.put("topic", ga.getTopic());
            t.put("scored", m.getReviewerScore() != null);
            t.put("reviewerScore", m.getReviewerScore());
            t.put("reviewerComment", m.getReviewerComment());
            out.add(t);
        }
        return out;
    }

    /** 评阅教师提交评分（0-100）+ 评语 */
    public Map<String, Object> submitReviewScore(Long advisorId, Long reviewerId, Integer score, String comment) {
        GraduateMilestone m = msRepo.findByAdvisorId(advisorId).stream()
                .filter(x -> "defense".equals(x.getStage())).findFirst().orElse(null);
        if (m == null) throw new IllegalArgumentException("流程节点异常：缺答辩节点");
        if (!reviewerId.equals(m.getReviewerId())) throw new IllegalArgumentException("您未被指派为该生的评阅教师");
        boolean midtermPassed = msRepo.findByAdvisorId(advisorId).stream()
                .anyMatch(x -> "midterm".equals(x.getStage()) && "passed".equals(x.getStatus()));
        if (!midtermPassed) throw new IllegalArgumentException("中期检查未通过，尚未进入评阅阶段");
        if (score == null || score < 0 || score > 100) throw new IllegalArgumentException("评分须为 0-100");
        m.setReviewerScore(score);
        m.setReviewerComment(comment == null ? "" : comment.trim());
        m.setReviewerAt(LocalDateTime.now());
        // 兼容历史数据：答辩节点已按旧 40/60 公式通过的学生，补录评阅分后按三方公式重算总评
        if ("passed".equals(m.getStatus()) && m.getAdvisorScore() != null && m.getDefenseScore() != null) {
            m.setTotalScore((int) Math.round(m.getAdvisorScore() * 0.4 + score * 0.2 + m.getDefenseScore() * 0.4));
        }
        msRepo.save(m);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("msg", "评阅评分已提交");
        r.put("score", score);
        return r;
    }

    // ============ 指导记录（维普/超星同款过程管理环节） ============

    public List<Map<String, Object>> guidanceList(Long advisorId, Long userId, String role) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) throw new IllegalArgumentException("工作台不存在");
        boolean ok = "admin".equals(role)
                || ("student".equals(role) && ga.getStudentId().equals(userId))
                || ("teacher".equals(role) && ga.getTeacherId().equals(userId));
        if (!ok) throw new IllegalArgumentException("无权查看");
        List<Map<String, Object>> out = new ArrayList<>();
        for (GraduateGuidanceRecord g : gRecRepo.findByAdvisorIdOrderByRecordDateDescIdDesc(advisorId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("recordDate", g.getRecordDate() != null ? g.getRecordDate().toString() : null);
            m.put("method", g.getMethod());
            m.put("content", g.getContent());
            m.put("createdAt", g.getCreatedAt() != null ? java.sql.Timestamp.valueOf(g.getCreatedAt()).getTime() : null);
            out.add(m);
        }
        return out;
    }

    public GraduateGuidanceRecord addGuidance(Long advisorId, Long teacherId, String recordDate, String method, String content) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) throw new IllegalArgumentException("工作台不存在");
        if (!ga.getTeacherId().equals(teacherId)) throw new IllegalArgumentException("仅导师可登记指导记录");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("指导内容不能为空");
        GraduateGuidanceRecord g = new GraduateGuidanceRecord();
        g.setAdvisorId(advisorId);
        g.setStudentId(ga.getStudentId());
        g.setTeacherId(teacherId);
        try {
            g.setRecordDate(recordDate == null || recordDate.isBlank()
                    ? java.time.LocalDate.now() : java.time.LocalDate.parse(recordDate));
        } catch (Exception e) {
            throw new IllegalArgumentException("指导日期格式应为 yyyy-MM-dd");
        }
        g.setMethod(method == null || method.isBlank() ? "面谈" : method);
        g.setContent(content.trim());
        g.setCreatedAt(LocalDateTime.now());
        return gRecRepo.save(g);
    }

    public void deleteGuidance(Long id, Long teacherId) {
        GraduateGuidanceRecord g = gRecRepo.findById(id).orElse(null);
        if (g == null) throw new IllegalArgumentException("记录不存在");
        if (!g.getTeacherId().equals(teacherId)) throw new IllegalArgumentException("只能删除本人登记的记录");
        gRecRepo.delete(g);
    }

    // ============ 查重登记（定稿 → 查重 → 达标获得答辩资格） ============

    /** 幂等创建：终稿通过后首次查询即生成记录 */
    public GraduatePlagiarism ensurePlagiarism(Long advisorId) {
        return plagRepo.findByAdvisorId(advisorId).orElseGet(() -> {
            GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
            if (ga == null) throw new IllegalArgumentException("工作台不存在");
            GraduatePlagiarism c = new GraduatePlagiarism();
            c.setAdvisorId(advisorId);
            c.setStudentId(ga.getStudentId());
            c.setTeacherId(ga.getTeacherId());
            c.setStatus("none");
            c.setCreatedAt(LocalDateTime.now());
            return plagRepo.save(c);
        });
    }

    public Map<String, Object> plagiarismView(Long advisorId, Long userId, String role) {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) throw new IllegalArgumentException("工作台不存在");
        boolean ok = "admin".equals(role)
                || ("student".equals(role) && ga.getStudentId().equals(userId))
                || ("teacher".equals(role) && ga.getTeacherId().equals(userId));
        if (!ok) throw new IllegalArgumentException("无权查看");
        GraduatePlagiarism c = ensurePlagiarism(advisorId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("status", c.getStatus());
        m.put("repeatRate", c.getRepeatRate());
        m.put("maxRate", PLAGIARISM_MAX);
        m.put("fileName", c.getFileName());
        m.put("fileSizeBytes", c.getFileSizeBytes());
        m.put("content", c.getContent());
        m.put("teacherComment", c.getTeacherComment());
        m.put("submittedAt", c.getSubmittedAt() != null ? java.sql.Timestamp.valueOf(c.getSubmittedAt()).getTime() : null);
        m.put("reviewedAt", c.getReviewedAt() != null ? java.sql.Timestamp.valueOf(c.getReviewedAt()).getTime() : null);
        // 操作标记：终稿通过后才可提交
        GraduateMilestone fin = msRepo.findByAdvisorId(advisorId).stream()
                .filter(x -> "final".equals(x.getStage())).findFirst().orElse(null);
        boolean finalPassed = fin != null && "passed".equals(fin.getStatus());
        m.put("canSubmit", "student".equals(role) && finalPassed
                && ("none".equals(c.getStatus()) || "returned".equals(c.getStatus())));
        m.put("canReview", "teacher".equals(role) && "submitted".equals(c.getStatus()));
        m.put("finalPassed", finalPassed);
        return m;
    }

    @Transactional
    public GraduatePlagiarism submitPlagiarism(Long advisorId, Long studentId, Double rate,
            String content, MultipartFile file) throws IOException {
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null || !ga.getStudentId().equals(studentId)) throw new IllegalArgumentException("无权提交");
        GraduateMilestone fin = msRepo.findByAdvisorId(advisorId).stream()
                .filter(x -> "final".equals(x.getStage())).findFirst().orElse(null);
        if (fin == null || !"passed".equals(fin.getStatus())) throw new IllegalArgumentException("论文终稿通过后才能提交查重登记");
        GraduatePlagiarism c = ensurePlagiarism(advisorId);
        if (!"none".equals(c.getStatus()) && !"returned".equals(c.getStatus()))
            throw new IllegalArgumentException("查重登记当前不可提交（待审核或已达标）");
        if (rate == null || rate < 0 || rate > 100) throw new IllegalArgumentException("重复率须为 0-100 的数字");
        if (content == null || content.isBlank() && (file == null || file.isEmpty()))
            throw new IllegalArgumentException("请填写查重系统说明或上传报告单");
        if (file != null && !file.isEmpty()) {
            long maxBytes = 100L * 1024 * 1024;
            if (file.getSize() > maxBytes) throw new IllegalArgumentException("文件超过 100MB");
            String origName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String lower = origName.toLowerCase();
            boolean okExt = lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")
                          || lower.endsWith(".txt") || lower.endsWith(".zip") || lower.endsWith(".rar");
            if (!okExt) throw new IllegalArgumentException("查重报告仅支持 pdf/doc/docx/txt/zip/rar");
            String safeName = origName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String relPath = "plagiarism/" + advisorId + "/" + ts + "_" + safeName;
            Path absDir = Paths.get(uploadRoot, "plagiarism", String.valueOf(advisorId));
            Files.createDirectories(absDir);
            Files.copy(file.getInputStream(), absDir.resolve(ts + "_" + safeName), StandardCopyOption.REPLACE_EXISTING);
            c.setFileName(safeName);
            c.setFilePath(relPath);
            c.setFileSizeBytes(file.getSize());
        }
        c.setRepeatRate(rate);
        if (content != null && !content.isBlank()) c.setContent(content.trim());
        c.setStatus("submitted");
        c.setSubmittedAt(LocalDateTime.now());
        return plagRepo.save(c);
    }

    @Transactional
    public GraduatePlagiarism reviewPlagiarism(Long id, Long teacherId, String decision, String comment) {
        GraduatePlagiarism c = plagRepo.findById(id).orElse(null);
        if (c == null) throw new IllegalArgumentException("查重登记不存在");
        if (!c.getTeacherId().equals(teacherId)) throw new IllegalArgumentException("无权审核");
        if (!"submitted".equals(c.getStatus())) throw new IllegalArgumentException("该登记没有待审核的提交");
        if ("passed".equals(decision)) {
            if (c.getRepeatRate() == null || c.getRepeatRate() > PLAGIARISM_MAX) {
                throw new IllegalArgumentException("重复率超过 " + PLAGIARISM_MAX + "%，不能达标（只能退回重新修改）");
            }
            c.setStatus("passed");
        } else if ("returned".equals(decision)) {
            c.setStatus("returned");
        } else {
            throw new IllegalArgumentException("decision 必须是 passed 或 returned");
        }
        if (comment != null) c.setTeacherComment(comment.trim());
        c.setReviewedAt(LocalDateTime.now());
        return plagRepo.save(c);
    }

    /** 查重报告下载（学生本人 / 指导教师 / 管理员 / 评阅教师） */
    public Path plagiarismAbsPath(Long advisorId, Long userId, String role) {
        GraduatePlagiarism c = plagRepo.findByAdvisorId(advisorId).orElse(null);
        if (c == null || c.getFilePath() == null) return null;
        GraduateAdvisor ga = gaRepo.findById(advisorId).orElse(null);
        if (ga == null) return null;
        boolean allowed = "admin".equals(role)
                || ("student".equals(role) && ga.getStudentId().equals(userId))
                || ("teacher".equals(role) && (ga.getTeacherId().equals(userId) || isReviewerOf(ga.getId(), userId)));
        return allowed ? Paths.get(uploadRoot, c.getFilePath()) : null;
    }

    private boolean isReviewerOf(Long advisorId, Long teacherId) {
        return msRepo.findByAdvisorId(advisorId).stream()
                .anyMatch(m -> "defense".equals(m.getStage()) && teacherId.equals(m.getReviewerId()));
    }

    /** 教务端毕业进度总览：双选/课题/各阶段漏斗 + 每对师生明细（停留天数/评阅指派/总评） */
    public Map<String, Object> adminGradOverview() {
        List<GraduateAdvisor> approved = gaRepo.findAll().stream()
                .filter(g -> "approved".equals(g.getStatus())).toList();
        int confirmed = approved.size();
        int withTopic = 0, openingPassed = 0, midtermPassed = 0, finalPassed = 0, plagPassed = 0, defenseDone = 0;
        List<Map<String, Object>> pairs = new ArrayList<>();
        for (GraduateAdvisor ga : approved) {
            User stu = userRepo.findById(ga.getStudentId()).orElse(null);
            User tea = userRepo.findById(ga.getTeacherId()).orElse(null);
            List<GraduateMilestone> ms = msRepo.findByAdvisorId(ga.getId());
            GraduateMilestone opening = stage(ms, "opening"), midterm = stage(ms, "midterm"),
                    fin = stage(ms, "final"), defense = stage(ms, "defense");
            boolean topicSet = ga.getTopic() != null && !ga.getTopic().isBlank();
            if (topicSet) withTopic++;
            if (opening != null && "passed".equals(opening.getStatus())) openingPassed++;
            if (midterm != null && "passed".equals(midterm.getStatus())) midtermPassed++;
            if (fin != null && "passed".equals(fin.getStatus())) finalPassed++;
            GraduatePlagiarism pl = plagRepo.findByAdvisorId(ga.getId()).orElse(null);
            if (pl != null && "passed".equals(pl.getStatus())) plagPassed++;
            if (defense != null && "passed".equals(defense.getStatus())) defenseDone++;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("advisorId", ga.getId());
            p.put("studentNo", stu != null ? stu.getNo() : "");
            p.put("studentName", stu != null ? stu.getName() : "未知");
            p.put("studentMajor", stu != null ? stu.getMajor() : "");
            p.put("advisorName", tea != null ? tea.getName() : "未知");
            p.put("topic", topicSet ? ga.getTopic() : "未确定课题");
            p.put("stage", currentStage(opening, midterm, fin, defense, topicSet));
            p.put("opening", statusCn(opening));
            p.put("midterm", statusCn(midterm));
            p.put("final", statusCn(fin));
            p.put("defense", statusCn(defense));
            p.put("reviewerName", defense != null ? defense.getReviewerName() : null);
            p.put("reviewerScore", defense != null ? defense.getReviewerScore() : null);
            p.put("totalScore", defense != null ? defense.getTotalScore() : null);
            p.put("guidanceCount", gRecRepo.countByAdvisorId(ga.getId()));
            GraduatePlagiarism plag = plagRepo.findByAdvisorId(ga.getId()).orElse(null);
            String plagStatus = plag == null ? "未提交"
                    : switch (plag.getStatus()) { case "submitted" -> "待审核"; case "passed" -> "已达标";
                        case "returned" -> "已退回"; default -> "未提交"; };
            p.put("plagiarism", plagStatus);
            p.put("repeatRate", plag != null ? plag.getRepeatRate() : null);
            // 答辩资格（派生）：中期通过 + 查重达标
            boolean qualified = "已达标".equals(plagStatus) && midterm != null && "passed".equals(midterm.getStatus());
            p.put("qualified", qualified);
            // 停留天数：当前阶段最近一次状态变更距今
            LocalDateTime last = lastTouch(opening, midterm, fin, defense, ga);
            p.put("stayDays", last == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(last.toLocalDate(), java.time.LocalDate.now()));
            pairs.add(p);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("studentTotal", userRepo.findByRole("student").size());
        out.put("confirmed", confirmed);
        out.put("withTopic", withTopic);
        out.put("openingPassed", openingPassed);
        out.put("midtermPassed", midtermPassed);
        out.put("finalPassed", finalPassed);
        out.put("plagPassed", plagPassed);
        out.put("defenseDone", defenseDone);
        out.put("pairs", pairs);
        return out;
    }

    private GraduateMilestone stage(List<GraduateMilestone> ms, String stage) {
        return ms.stream().filter(m -> stage.equals(m.getStage())).findFirst().orElse(null);
    }

    private String currentStage(GraduateMilestone opening, GraduateMilestone midterm,
                                GraduateMilestone fin, GraduateMilestone defense, boolean topicSet) {
        if (defense != null && "passed".equals(defense.getStatus())) return "答辩完成";
        if (fin != null && "passed".equals(fin.getStatus())) return "论文终稿已通过";
        if (fin != null && "submitted".equals(fin.getStatus())) return "论文终稿待审";
        if (midterm != null && "passed".equals(midterm.getStatus())) return "评阅与答辩准备";
        if (midterm != null && "submitted".equals(midterm.getStatus())) return "中期检查待审";
        if (opening != null && "passed".equals(opening.getStatus())) return "中期进行中";
        if (opening != null && "submitted".equals(opening.getStatus())) return "开题报告待审";
        if (opening != null && "returned".equals(opening.getStatus())) return "开题报告已退回";
        return topicSet ? "待提交开题报告" : "待确定课题";
    }

    private String statusCn(GraduateMilestone m) {
        if (m == null) return "—";
        return switch (m.getStatus()) {
            case "none" -> "未提交";
            case "submitted" -> "待审核";
            case "passed" -> "已通过";
            case "returned" -> "已退回";
            default -> m.getStatus();
        };
    }

    private LocalDateTime lastTouch(GraduateMilestone opening, GraduateMilestone midterm,
                                    GraduateMilestone fin, GraduateMilestone defense, GraduateAdvisor ga) {
        LocalDateTime latest = ga.getUpdatedAt() != null ? ga.getUpdatedAt() : ga.getCreatedAt();
        for (GraduateMilestone m : new GraduateMilestone[]{opening, midterm, fin, defense}) {
            if (m == null) continue;
            if (m.getReviewedAt() != null && m.getReviewedAt().isAfter(latest)) latest = m.getReviewedAt();
            if (m.getSubmittedAt() != null && m.getSubmittedAt().isAfter(latest)) latest = m.getSubmittedAt();
        }
        return latest;
    }

    /** 毕设总评（答辩节点合成结果；未出成绩返回 null） */
    public Integer thesisScore(Long advisorId) {
        return msRepo.findByAdvisorId(advisorId).stream()
                .filter(m -> "defense".equals(m.getStage()))
                .map(GraduateMilestone::getTotalScore)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /** 按学生取毕设总评（找其已确认导师关系对应工作台；无则 null） */
    public Integer studentThesisScore(Long studentId) {
        return studentApproved(studentId).stream()
                .map(ga -> thesisScore(ga.getId()))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    /** 里程碑文件绝对路径（学生本人 / 指导教师 / 管理员） */
    public Path milestoneAbsPath(Long id, Long userId, String role) {
        GraduateMilestone m = msRepo.findById(id).orElse(null);
        if (m == null || m.getFilePath() == null) return null;
        GraduateAdvisor ga = gaRepo.findById(m.getAdvisorId()).orElse(null);
        if (ga == null) return null;
        if ("student".equals(role) && !ga.getStudentId().equals(userId)) return null;
        if ("teacher".equals(role) && !ga.getTeacherId().equals(userId)) return null;
        return Paths.get(uploadRoot, m.getFilePath());
    }

    private static String stageName(String stage) {
        return switch (stage) {
            case "opening" -> "开题报告";
            case "midterm" -> "中期检查";
            case "final" -> "论文终稿";
            case "defense" -> "答辩";
            default -> stage;
        };
    }

    private Map<String, Object> milestoneMap(GraduateMilestone m) {
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("id", m.getId());
        mm.put("advisorId", m.getAdvisorId());
        mm.put("stage", m.getStage());
        mm.put("stageName", stageName(m.getStage()));
        mm.put("status", m.getStatus());
        mm.put("title", m.getTitle());
        mm.put("content", m.getContent());
        mm.put("fileName", m.getFileName());
        mm.put("fileSizeBytes", m.getFileSizeBytes());
        mm.put("teacherComment", m.getTeacherComment());
        mm.put("advisorScore", m.getAdvisorScore());
        mm.put("reviewerId", m.getReviewerId());
        mm.put("reviewerName", m.getReviewerName());
        mm.put("reviewerScore", m.getReviewerScore());
        mm.put("reviewerComment", m.getReviewerComment());
        mm.put("reviewerAt", m.getReviewerAt() != null ? java.sql.Timestamp.valueOf(m.getReviewerAt()).getTime() : null);
        mm.put("defenseScore", m.getDefenseScore());
        mm.put("totalScore", m.getTotalScore());
        mm.put("submittedAt", m.getSubmittedAt() != null ? java.sql.Timestamp.valueOf(m.getSubmittedAt()).getTime() : null);
        mm.put("reviewedAt", m.getReviewedAt() != null ? java.sql.Timestamp.valueOf(m.getReviewedAt()).getTime() : null);
        return mm;
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
