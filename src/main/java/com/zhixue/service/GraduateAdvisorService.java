package com.zhixue.service;

import com.zhixue.entity.AdvisorCapacity;
import com.zhixue.entity.GraduateAdvisor;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.AdvisorCapacityRepository;
import com.zhixue.repository.GraduateAdvisorRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class GraduateAdvisorService {

    @Autowired private GraduateAdvisorRepository gaRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository cfgRepo;
    @Autowired private AdvisorCapacityRepository capRepo;

    private static final int DEFAULT_CAPACITY = 15;

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

    public List<Map<String, Object>> listTeachers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User t : userRepo.findByRole("teacher")) {
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
        for (GraduateAdvisor g : gaRepo.findByStudentIdOrderByIdDesc(studentId)) out.add(row(g, false, true));
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
        int cap = teacherCapacity(teacherId);
        long approved = gaRepo.countByTeacherIdAndStatus(teacherId, "approved");
        if (approved >= cap) {
            r.put("success", false); r.put("msg", "该导师已达容量上限(" + cap + "人)"); return r;
        }

        GraduateAdvisor g = new GraduateAdvisor();
        g.setStudentId(studentId);
        g.setTeacherId(teacherId);
        g.setStatus("pending");
        g.setTopic(topic == null ? "" : topic.trim());
        g.setPreferenceText(pref == null ? "" : pref.trim());
        g.setCreatedAt(LocalDateTime.now());
        g.setUpdatedAt(LocalDateTime.now());
        gaRepo.save(g);
        r.put("success", true);
        r.put("msg", "申请已提交，等待导师审批");
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
}
