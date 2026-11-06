package com.zhixue.service;

import com.zhixue.entity.AdvisorCapacity;
import com.zhixue.entity.GraduateAdvisor;
import com.zhixue.entity.GraduateTopic;
import com.zhixue.entity.GraduateTopicProposal;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.AdvisorCapacityRepository;
import com.zhixue.repository.GraduateAdvisorRepository;
import com.zhixue.repository.GraduateTopicProposalRepository;
import com.zhixue.repository.GraduateTopicRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraduateAdvisorService {

    @Autowired private GraduateAdvisorRepository gaRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository cfgRepo;
    @Autowired private AdvisorCapacityRepository capRepo;
    @Autowired private GraduateTopicRepository topicRepo;
    @Autowired private GraduateTopicProposalRepository proposalRepo;

    private static final int DEFAULT_CAPACITY = 15;
    /** 多志愿双选：学生同时最多可保留的 pending 志愿数 */
    private static final int MAX_CHOICES = 3;

    private SystemConfig cfg() {
        return cfgRepo.findById(1L).orElseGet(() -> {
            SystemConfig c = new SystemConfig();
            cfgRepo.save(c);
            return c;
        });
    }

    public boolean isOpen() {
        Boolean v = cfg().getAdvisorOpen();
        return v == null || v;
    }

    public int defaultCapacity() {
        Integer v = cfg().getAdvisorDefaultCapacity();
        return (v == null || v <= 0) ? DEFAULT_CAPACITY : v;
    }

    public int teacherCapacity(Long teacherId) {
        Optional<AdvisorCapacity> ac = capRepo.findById(teacherId);
        if (ac.isPresent() && ac.get().getCapacity() != null && ac.get().getCapacity() > 0) {
            return ac.get().getCapacity();
        }
        return defaultCapacity();
    }

    public Map<String, Object> cfgMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", isOpen());
        m.put("defaultCapacity", defaultCapacity());
        return m;
    }

    public Map<String, Object> row(GraduateAdvisor g, boolean withStudent, boolean withTeacher) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("studentId", g.getStudentId());
        m.put("teacherId", g.getTeacherId());
        m.put("status", g.getStatus());
        m.put("priority", g.getPriority() == null ? 1 : g.getPriority());
        m.put("topic", g.getTopic());
        m.put("preferenceText", g.getPreferenceText());
        m.put("teacherReply", g.getTeacherReply());
        m.put("createdAt", g.getCreatedAt() != null ? java.sql.Timestamp.valueOf(g.getCreatedAt()).getTime() : null);
        m.put("updatedAt", g.getUpdatedAt() != null ? java.sql.Timestamp.valueOf(g.getUpdatedAt()).getTime() : null);

        if (withStudent) {
            User s = userRepo.findById(g.getStudentId()).orElse(null);
            if (s != null) {
                m.put("studentName", s.getName());
                m.put("studentNo", s.getNo());
                m.put("studentMajor", s.getMajor());
                m.put("studentClass", s.getClassNo());
            } else {
                m.put("studentName", "（学生已删除）");
            }
        }
        if (withTeacher) {
            User t = userRepo.findById(g.getTeacherId()).orElse(null);
            if (t != null) {
                m.put("teacherName", t.getName());
                m.put("teacherNo", t.getNo());
                m.put("teacherCollege", t.getCollege());
                m.put("teacherMajor", t.getMajor());
                m.put("teacherCapacity", teacherCapacity(t.getId()));
                m.put("teacherApproved", gaRepo.countByTeacherIdAndStatus(t.getId(), "approved"));
                m.put("teacherPending", gaRepo.countByTeacherIdAndStatus(t.getId(), "pending"));
            } else {
                m.put("teacherName", "（教师已删除）");
            }
        }
        return m;
    }

    /**
     * 导师列表：学生按学院强制隔离（学院同步——本院学生只看本院导师），
     * 学生未设置学院时退化为全量（防御旧数据）。
     */
    public List<Map<String, Object>> listTeachers(User viewer) {
        boolean isolated = viewer != null && "student".equals(viewer.getRole())
                && viewer.getCollege() != null && !viewer.getCollege().isBlank();
        List<Map<String, Object>> out = new ArrayList<>();
        for (User t : userRepo.findByRole("teacher")) {
            if (isolated && !viewer.getCollege().equals(t.getCollege())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teacherId", t.getId());
            m.put("teacherName", t.getName());
            m.put("teacherNo", t.getNo());
            m.put("college", t.getCollege());
            m.put("major", t.getMajor());
            int cap = teacherCapacity(t.getId());
            long approved = gaRepo.countByTeacherIdAndStatus(t.getId(), "approved");
            long pending = gaRepo.countByTeacherIdAndStatus(t.getId(), "pending");
            m.put("capacity", cap);
            m.put("approved", approved);
            m.put("pending", pending);
            m.put("available", approved < cap);
            out.add(m);
        }
        out.sort((a, b) -> {
            boolean aAv = (Boolean) a.get("available");
            boolean bAv = (Boolean) b.get("available");
            if (aAv != bAv) return aAv ? -1 : 1;
            return ((Long) a.get("approved")).compareTo((Long) b.get("approved"));
        });
        return out;
    }

    public List<Map<String, Object>> mineStudent(Long studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<GraduateAdvisor> list = new ArrayList<>(gaRepo.findByStudentIdOrderByIdDesc(studentId));
        // 志愿序升序展示（第一志愿在最前）
        list.sort((a, b) -> Integer.compare(
                a.getPriority() == null ? 1 : a.getPriority(),
                b.getPriority() == null ? 1 : b.getPriority()));
        for (GraduateAdvisor g : list) out.add(row(g, false, true));
        return out;
    }

    public List<Map<String, Object>> mineTeacher(Long teacherId, String status) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<GraduateAdvisor> list = (status != null && !status.isBlank())
                ? gaRepo.findByTeacherIdAndStatusOrderByIdDesc(teacherId, status)
                : gaRepo.findByTeacherIdOrderByIdDesc(teacherId);
        for (GraduateAdvisor g : list) out.add(row(g, true, false));
        return out;
    }

    public Map<String, Object> teacherStats(Long teacherId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("teacherId", teacherId);
        m.put("capacity", teacherCapacity(teacherId));
        m.put("approved", gaRepo.countByTeacherIdAndStatus(teacherId, "approved"));
        m.put("pending", gaRepo.countByTeacherIdAndStatus(teacherId, "pending"));
        m.put("rejected", gaRepo.countByTeacherIdAndStatus(teacherId, "rejected"));
        m.put("cancelled", gaRepo.countByTeacherIdAndStatus(teacherId, "cancelled"));
        return m;
    }

    public Map<String, Object> adminOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        int withSpace = 0, withPending = 0;
        for (User t : userRepo.findByRole("teacher")) {
            int cap = teacherCapacity(t.getId());
            long ap = gaRepo.countByTeacherIdAndStatus(t.getId(), "approved");
            if (ap < cap) withSpace++;
            long pd = gaRepo.countByTeacherIdAndStatus(t.getId(), "pending");
            if (pd > 0) withPending++;
        }
        out.put("config", cfgMap());
        out.put("teacherCount", userRepo.findByRole("teacher").size());
        out.put("teachersWithSpace", withSpace);
        out.put("teachersWithPending", withPending);
        out.put("totalApproved", gaRepo.countByStatus("approved"));
        out.put("totalPending", gaRepo.countByStatus("pending"));
        out.put("totalRejected", gaRepo.countByStatus("rejected"));
        out.put("totalCancelled", gaRepo.countByStatus("cancelled"));
        return out;
    }

    public List<Map<String, Object>> adminList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GraduateAdvisor g : gaRepo.findAll()) out.add(row(g, true, true));
        out.sort((a, b) -> Long.compare((Long) b.get("id"), (Long) a.get("id")));
        return out;
    }

    public List<Map<String, Object>> allTeacherCapacities() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User t : userRepo.findByRole("teacher")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teacherId", t.getId());
            m.put("teacherName", t.getName());
            m.put("teacherNo", t.getNo());
            m.put("teacherCollege", t.getCollege());
            m.put("capacity", teacherCapacity(t.getId()));
            m.put("approved", gaRepo.countByTeacherIdAndStatus(t.getId(), "approved"));
            m.put("pending", gaRepo.countByTeacherIdAndStatus(t.getId(), "pending"));
            out.add(m);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> apply(Long studentId, Long teacherId, String topic, String pref) {
        Map<String, Object> r = new HashMap<>();
        if (!isOpen()) { r.put("success", false); r.put("msg", "导师选择当前已关闭"); return r; }

        User teacher = userRepo.findById(teacherId).orElse(null);
        if (teacher == null || !"teacher".equals(teacher.getRole())) {
            r.put("success", false); r.put("msg", "导师不存在"); return r;
        }
        if (gaRepo.findByStudentIdAndStatus(studentId, "approved").isPresent()) {
            r.put("success", false); r.put("msg", "你已有已确认的导师，不可再申请"); return r;
        }
        List<String> blocking = Arrays.asList("pending", "approved");
        if (gaRepo.findByStudentIdAndTeacherIdAndStatusIn(studentId, teacherId, blocking).isPresent()) {
            r.put("success", false); r.put("msg", "你已在向该导师申请中或已确认"); return r;
        }
        // 多志愿双选：同时最多保留 3 个待审志愿，志愿序自动递增
        List<GraduateAdvisor> pendings = gaRepo.findByStudentIdOrderByIdDesc(studentId).stream()
                .filter(g -> "pending".equals(g.getStatus())).toList();
        if (pendings.size() >= MAX_CHOICES) {
            r.put("success", false); r.put("msg", "最多同时保留 " + MAX_CHOICES + " 个志愿，请先撤销或等待已有志愿出结果");
            return r;
        }
        int cap = teacherCapacity(teacherId);
        long approved = gaRepo.countByTeacherIdAndStatus(teacherId, "approved");
        if (approved >= cap) {
            r.put("success", false); r.put("msg", "该导师已达容量上限(" + cap + "人)"); return r;
        }

        GraduateAdvisor g = new GraduateAdvisor();
        g.setStudentId(studentId);
        g.setTeacherId(teacherId);
        g.setStatus("pending");
        g.setPriority(pendings.stream().mapToInt(x -> x.getPriority() == null ? 1 : x.getPriority()).max().orElse(0) + 1);
        g.setTopic(topic == null ? "" : topic.trim());
        g.setPreferenceText(pref == null ? "" : pref.trim());
        g.setCreatedAt(LocalDateTime.now());
        g.setUpdatedAt(LocalDateTime.now());
        gaRepo.save(g);
        r.put("success", true);
        r.put("msg", "已提交为第 " + g.getPriority() + " 志愿，等待导师审批");
        r.put("data", row(g, false, true));
        return r;
    }

    @Transactional
    public Map<String, Object> cancelByStudent(Long studentId, Long id) {
        Map<String, Object> r = new HashMap<>();
        GraduateAdvisor g = gaRepo.findById(id).orElse(null);
        if (g == null || !g.getStudentId().equals(studentId)) {
            r.put("success", false); r.put("msg", "申请不存在"); return r;
        }
        if (!"pending".equals(g.getStatus())) {
            r.put("success", false); r.put("msg", "仅可撤销申请中状态"); return r;
        }
        g.setStatus("cancelled");
        g.setUpdatedAt(LocalDateTime.now());
        gaRepo.save(g);
        r.put("success", true); r.put("msg", "申请已撤销");
        return r;
    }

    @Transactional
    public Map<String, Object> decide(Long teacherId, Long id, String decision, String reply) {
        Map<String, Object> r = new HashMap<>();
        GraduateAdvisor g = gaRepo.findById(id).orElse(null);
        if (g == null || !g.getTeacherId().equals(teacherId)) {
            r.put("success", false); r.put("msg", "申请不存在"); return r;
        }
        if (!"pending".equals(g.getStatus())) {
            r.put("success", false); r.put("msg", "该申请已处理过"); return r;
        }
        if ("approve".equals(decision)) {
            int cap = teacherCapacity(teacherId);
            long approved = gaRepo.countByTeacherIdAndStatus(teacherId, "approved");
            if (approved >= cap) {
                r.put("success", false); r.put("msg", "导师已满(" + cap + "人)，无法再批准"); return r;
            }
            Optional<GraduateAdvisor> dup = gaRepo.findByStudentIdAndStatus(g.getStudentId(), "approved");
            if (dup.isPresent()) {
                g.setStatus("rejected");
                g.setTeacherReply("冲突：该学生已确认其他导师");
                g.setUpdatedAt(LocalDateTime.now());
                gaRepo.save(g);
                r.put("success", false); r.put("msg", "该学生已确认其他导师，本申请自动拒绝"); return r;
            }
            g.setStatus("approved");
            g.setTeacherReply(reply == null ? "" : reply);
            g.setUpdatedAt(LocalDateTime.now());
            gaRepo.save(g);
            // 多志愿递延：学生其余待审志愿自动失效（真实高校双选规则：一经确认，其他志愿作废）
            User teacherUser = userRepo.findById(teacherId).orElse(null);
            String teacherName = teacherUser != null ? teacherUser.getName() : "导师";
            for (GraduateAdvisor other : gaRepo.findByStudentIdOrderByIdDesc(g.getStudentId())) {
                if ("pending".equals(other.getStatus())) {
                    other.setStatus("rejected");
                    other.setTeacherReply("你已在第 " + g.getPriority() + " 志愿被「" + teacherName + "」确认，本志愿自动失效");
                    other.setUpdatedAt(LocalDateTime.now());
                    gaRepo.save(other);
                }
            }
            r.put("success", true); r.put("msg", "已批准");
        } else if ("reject".equals(decision)) {
            g.setStatus("rejected");
            g.setTeacherReply(reply == null ? "" : reply);
            g.setUpdatedAt(LocalDateTime.now());
            gaRepo.save(g);
            r.put("success", true); r.put("msg", "已拒绝");
        } else {
            r.put("success", false); r.put("msg", "decision 必须是 approve 或 reject");
        }
        return r;
    }

    @Transactional
    public Map<String, Object> saveCfg(Boolean open, Integer defaultCapacity, Long teacherId, Integer teacherCapacity) {
        Map<String, Object> r = new HashMap<>();
        SystemConfig c = cfg();
        if (open != null) c.setAdvisorOpen(open);
        if (defaultCapacity != null) c.setAdvisorDefaultCapacity(defaultCapacity);
        cfgRepo.save(c);
        if (teacherId != null && teacherCapacity != null) {
            AdvisorCapacity ac = capRepo.findById(teacherId).orElse(new AdvisorCapacity());
            ac.setTeacherId(teacherId);
            ac.setCapacity(teacherCapacity);
            capRepo.save(ac);
        }
        r.put("success", true);
        r.put("data", cfgMap());
        return r;
    }

    @Transactional
    public Map<String, Object> adminDelete(Long id) {
        Map<String, Object> r = new HashMap<>();
        GraduateAdvisor g = gaRepo.findById(id).orElse(null);
        if (g == null) { r.put("success", false); r.put("msg", "记录不存在"); return r; }
        gaRepo.delete(g);
        r.put("success", true);
        return r;
    }

    // ============ 课题库（按学院分组：教师学院即课题归属学院） ============

    /**
     * 课题列表：college 为空返回全部；教师/学生按学院强制隔离（学院同步：
     * 什么学院的学生只能看到什么学院的课题库，传入的 college 参数对隔离角色无效），
     * 返回的 colleges 聚合同样按角色收窄。学生未设置学院时退化为全量（防御旧数据）。
     * taken = 课题名与某条已确认导师关系的课题一致
     */
    public Map<String, Object> topicList(String college, User viewer) {
        boolean teacherView = viewer != null && "teacher".equals(viewer.getRole());
        boolean studentView = viewer != null && "student".equals(viewer.getRole());
        boolean isolated = (teacherView || studentView)
                && viewer.getCollege() != null && !viewer.getCollege().isBlank();
        List<GraduateTopic> topics = topicRepo.findByStatusOrderByIdDesc("open");
        // 已确认导师关系的课题名视为已选定
        Set<String> takenTitles = new HashSet<>();
        for (GraduateAdvisor g : gaRepo.findByStatus("approved")) {
            if (g.getTopic() != null && !g.getTopic().isBlank()) takenTitles.add(g.getTopic().trim());
        }
        Map<Long, User> teacherCache = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        // 隔离角色忽略任何传入的 college 参数，只看本院
        String filterCollege = isolated ? viewer.getCollege() : college;
        for (GraduateTopic t : topics) {
            User teacher = teacherCache.computeIfAbsent(t.getTeacherId(), id -> userRepo.findById(id).orElse(null));
            String tc = teacher != null ? teacher.getCollege() : "";
            if (filterCollege != null && !filterCollege.isBlank() && !filterCollege.equals(tc)) continue;
            out.add(topicMap(t, teacher, takenTitles.contains(t.getTitle().trim())));
        }
        // 学院聚合（动态生成学院 Tab）：隔离角色只返回本院
        Set<String> colleges = new LinkedHashSet<>();
        if (isolated) {
            colleges.add(viewer.getCollege());
        } else {
            for (GraduateTopic t : topics) {
                User teacher = teacherCache.computeIfAbsent(t.getTeacherId(), id -> userRepo.findById(id).orElse(null));
                if (teacher != null && teacher.getCollege() != null && !teacher.getCollege().isBlank()) {
                    colleges.add(teacher.getCollege());
                }
            }
        }
        // 双选已完成的学生：额外带上自己导师的课题（即使跨学院）——「题库只显示自己导师的毕设题目」的数据基础
        if (studentView) {
            User me = viewer;
            GraduateAdvisor approved = gaRepo.findByStudentIdOrderByIdDesc(me.getId()).stream()
                    .filter(g -> "approved".equals(g.getStatus())).findFirst().orElse(null);
            if (approved != null) {
                Set<Long> included = out.stream().map(m -> (Long) m.get("id")).collect(Collectors.toSet());
                User advTeacher = teacherCache.computeIfAbsent(approved.getTeacherId(), id -> userRepo.findById(id).orElse(null));
                if (advTeacher != null && advTeacher.getCollege() != null && !advTeacher.getCollege().isBlank()) {
                    colleges.add(advTeacher.getCollege());
                }
                for (GraduateTopic t : topics) {
                    if (t.getTeacherId().equals(approved.getTeacherId()) && !included.contains(t.getId())) {
                        out.add(topicMap(t, advTeacher, takenTitles.contains(t.getTitle().trim())));
                    }
                }
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("topics", out);
        r.put("colleges", colleges);
        return r;
    }

    /** 教师自己的课题（含已下架） */
    public List<Map<String, Object>> myTopics(Long teacherId) {
        List<Map<String, Object>> out = new ArrayList<>();
        User teacher = userRepo.findById(teacherId).orElse(null);
        for (GraduateTopic t : topicRepo.findByTeacherIdOrderByIdDesc(teacherId)) {
            out.add(topicMap(t, teacher, false));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> createTopic(Long teacherId, String title, String direction, String description) {
        Map<String, Object> r = new HashMap<>();
        if (title == null || title.isBlank()) { r.put("success", false); r.put("msg", "课题名称不能为空"); return r; }
        GraduateTopic t = new GraduateTopic();
        t.setTeacherId(teacherId);
        t.setTitle(title.trim());
        t.setDirection(direction == null ? "" : direction.trim());
        t.setDescription(description == null ? "" : description.trim());
        t.setStatus("open");
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        topicRepo.save(t);
        r.put("success", true);
        r.put("msg", "课题已发布到课题库");
        r.put("data", topicMap(t, userRepo.findById(teacherId).orElse(null), false));
        return r;
    }

    @Transactional
    public Map<String, Object> updateTopic(Long teacherId, Long id, String title, String direction,
            String description, String status) {
        Map<String, Object> r = new HashMap<>();
        GraduateTopic t = topicRepo.findById(id).orElse(null);
        if (t == null || !t.getTeacherId().equals(teacherId)) {
            r.put("success", false); r.put("msg", "课题不存在"); return r;
        }
        if (title != null && !title.isBlank()) t.setTitle(title.trim());
        if (direction != null) t.setDirection(direction.trim());
        if (description != null) t.setDescription(description.trim());
        if ("open".equals(status) || "closed".equals(status)) t.setStatus(status);
        t.setUpdatedAt(LocalDateTime.now());
        topicRepo.save(t);
        r.put("success", true);
        r.put("msg", "课题已更新");
        r.put("data", topicMap(t, userRepo.findById(teacherId).orElse(null), false));
        return r;
    }

    @Transactional
    public Map<String, Object> deleteTopic(Long teacherId, Long id) {
        Map<String, Object> r = new HashMap<>();
        GraduateTopic t = topicRepo.findById(id).orElse(null);
        if (t == null || !t.getTeacherId().equals(teacherId)) {
            r.put("success", false); r.put("msg", "课题不存在"); return r;
        }
        topicRepo.delete(t);
        r.put("success", true);
        r.put("msg", "课题已删除");
        return r;
    }

    private Map<String, Object> topicMap(GraduateTopic t, User teacher, boolean taken) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("teacherId", t.getTeacherId());
        m.put("title", t.getTitle());
        m.put("direction", t.getDirection());
        m.put("description", t.getDescription());
        m.put("status", t.getStatus());
        m.put("taken", taken);
        m.put("teacherName", teacher != null ? teacher.getName() : "（教师已删除）");
        m.put("teacherNo", teacher != null ? teacher.getNo() : "");
        m.put("college", teacher != null ? teacher.getCollege() : "");
        m.put("createdAt", t.getCreatedAt() != null ? java.sql.Timestamp.valueOf(t.getCreatedAt()).getTime() : null);
        return m;
    }

    // ============ 自选课题（学生自拟 → 毕业导师审批，通过即写入双选课题） ============

    /** 学生提交自选课题：须已完成导师双选，且当前无待审核的自选课题 */
    @Transactional
    public Map<String, Object> submitProposal(Long studentId, String title, String direction, String description) {
        Map<String, Object> r = new HashMap<>();
        GraduateAdvisor approved = approvedRelationOf(studentId);
        if (approved == null) {
            r.put("success", false); r.put("msg", "请先完成导师双选，再提交自选课题"); return r;
        }
        if (title == null || title.isBlank()) {
            r.put("success", false); r.put("msg", "请填写课题名称"); return r;
        }
        title = title.trim();
        if (title.length() > 120) {
            r.put("success", false); r.put("msg", "课题名称不能超过 120 字"); return r;
        }
        if (proposalRepo.findByStudentIdAndStatus(studentId, "pending").isPresent()) {
            r.put("success", false); r.put("msg", "已有待审核的自选课题，请等待导师审核"); return r;
        }
        GraduateTopicProposal p = new GraduateTopicProposal();
        p.setStudentId(studentId);
        p.setTeacherId(approved.getTeacherId());
        p.setTitle(title);
        p.setDirection(direction == null ? "" : direction.trim());
        p.setDescription(description == null ? "" : description.trim());
        p.setStatus("pending");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        proposalRepo.save(p);
        r.put("success", true);
        r.put("msg", "自选课题已提交，等待导师审核");
        r.put("data", proposalRow(p, true));
        return r;
    }

    /** 学生修改被打回的自选课题并重新提交 */
    @Transactional
    public Map<String, Object> reviseProposal(Long studentId, Long id, String title, String direction, String description) {
        Map<String, Object> r = new HashMap<>();
        GraduateTopicProposal p = proposalRepo.findById(id).orElse(null);
        if (p == null || !p.getStudentId().equals(studentId)) {
            r.put("success", false); r.put("msg", "申请不存在"); return r;
        }
        if (!"rejected".equals(p.getStatus())) {
            r.put("success", false); r.put("msg", "仅被打回的课题可修改后重新提交"); return r;
        }
        if (title == null || title.isBlank()) {
            r.put("success", false); r.put("msg", "请填写课题名称"); return r;
        }
        title = title.trim();
        if (title.length() > 120) {
            r.put("success", false); r.put("msg", "课题名称不能超过 120 字"); return r;
        }
        p.setTitle(title);
        p.setDirection(direction == null ? "" : direction.trim());
        p.setDescription(description == null ? "" : description.trim());
        p.setStatus("pending");
        p.setTeacherReply(null);
        p.setUpdatedAt(LocalDateTime.now());
        proposalRepo.save(p);
        r.put("success", true);
        r.put("msg", "已重新提交，等待导师审核");
        r.put("data", proposalRow(p, true));
        return r;
    }

    /** 学生撤回待审核的自选课题 */
    @Transactional
    public Map<String, Object> withdrawProposal(Long studentId, Long id) {
        Map<String, Object> r = new HashMap<>();
        GraduateTopicProposal p = proposalRepo.findById(id).orElse(null);
        if (p == null || !p.getStudentId().equals(studentId)) {
            r.put("success", false); r.put("msg", "申请不存在"); return r;
        }
        if (!"pending".equals(p.getStatus())) {
            r.put("success", false); r.put("msg", "仅待审核状态可撤回"); return r;
        }
        p.setStatus("withdrawn");
        p.setUpdatedAt(LocalDateTime.now());
        proposalRepo.save(p);
        r.put("success", true);
        r.put("msg", "自选课题已撤回");
        return r;
    }

    /** 导师审批自选课题：approve → 写入双选关系的课题（流程「确定课题」往下走）；reject → 打回给学生修改 */
    @Transactional
    public Map<String, Object> decideProposal(Long teacherId, Long id, String decision, String reply) {
        Map<String, Object> r = new HashMap<>();
        if (!"approve".equals(decision) && !"reject".equals(decision)) {
            r.put("success", false); r.put("msg", "decision 必须是 approve 或 reject"); return r;
        }
        GraduateTopicProposal p = proposalRepo.findById(id).orElse(null);
        if (p == null || !p.getTeacherId().equals(teacherId)) {
            r.put("success", false); r.put("msg", "申请不存在"); return r;
        }
        if (!"pending".equals(p.getStatus())) {
            r.put("success", false); r.put("msg", "该课题已处理过"); return r;
        }
        p.setStatus("approve".equals(decision) ? "approved" : "rejected");
        p.setTeacherReply(reply == null ? "" : reply.trim());
        p.setUpdatedAt(LocalDateTime.now());
        proposalRepo.save(p);

        if ("approve".equals(decision)) {
            // 通过 → 写入该学生双选关系里的课题（双选流程「确定课题」完成）
            GraduateAdvisor approved = gaRepo.findByStudentIdOrderByIdDesc(p.getStudentId()).stream()
                    .filter(g -> "approved".equals(g.getStatus()) && g.getTeacherId().equals(teacherId))
                    .findFirst().orElse(null);
            if (approved != null) {
                approved.setTopic(p.getTitle());
                approved.setUpdatedAt(LocalDateTime.now());
                gaRepo.save(approved);
            }
            r.put("msg", "已通过「" + p.getTitle() + "」，课题已同步到该学生的双选进度");
        } else {
            r.put("msg", "已打回「" + p.getTitle() + "」，学生可修改后重新提交");
        }
        r.put("success", true);
        r.put("data", proposalRow(p, true));
        return r;
    }

    /** 学生：我的自选课题记录 */
    public List<Map<String, Object>> myProposals(Long studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GraduateTopicProposal p : proposalRepo.findByStudentIdOrderByIdDesc(studentId)) {
            out.add(proposalRow(p, true));
        }
        return out;
    }

    /** 导师：收到的自选课题（可按状态过滤） */
    public List<Map<String, Object>> teacherProposals(Long teacherId, String status) {
        List<GraduateTopicProposal> list = (status != null && !status.isBlank())
                ? proposalRepo.findByTeacherIdAndStatusOrderByIdDesc(teacherId, status)
                : proposalRepo.findByTeacherIdOrderByIdDesc(teacherId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (GraduateTopicProposal p : list) out.add(proposalRow(p, true));
        return out;
    }

    private GraduateAdvisor approvedRelationOf(Long studentId) {
        return gaRepo.findByStudentIdOrderByIdDesc(studentId).stream()
                .filter(g -> "approved".equals(g.getStatus()))
                .findFirst().orElse(null);
    }

    private Map<String, Object> proposalRow(GraduateTopicProposal p, boolean withNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("studentId", p.getStudentId());
        m.put("teacherId", p.getTeacherId());
        m.put("title", p.getTitle());
        m.put("direction", p.getDirection());
        m.put("description", p.getDescription());
        m.put("status", p.getStatus());
        m.put("teacherReply", p.getTeacherReply());
        m.put("createdAt", p.getCreatedAt() != null ? java.sql.Timestamp.valueOf(p.getCreatedAt()).getTime() : null);
        m.put("updatedAt", p.getUpdatedAt() != null ? java.sql.Timestamp.valueOf(p.getUpdatedAt()).getTime() : null);
        if (withNames) {
            User s = userRepo.findById(p.getStudentId()).orElse(null);
            m.put("studentName", s != null ? s.getName() : "（学生已删除）");
            m.put("studentNo", s != null ? s.getNo() : "");
            m.put("studentMajor", s != null ? s.getMajor() : "");
            m.put("studentClass", s != null ? s.getClassNo() : "");
            m.put("studentCollege", s != null ? s.getCollege() : "");
            User t = userRepo.findById(p.getTeacherId()).orElse(null);
            m.put("teacherName", t != null ? t.getName() : "（教师已删除）");
        }
        return m;
    }
}
