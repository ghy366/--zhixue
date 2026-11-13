package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.DropRequest;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.DropRequestRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/** 轮次结束后的退课申请闭环：学生发起 → 任课老师审批 → 通过则真实退课并站内通知学生。 */
@Service
public class DropRequestService {

    @Autowired private DropRequestRepository repo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private NotificationService notificationService;
    @Autowired private EnrollmentService enrollmentService;

    /** 学生点退课被拒后调用：同一学生/课程/轮次的 pending 申请只创建一次 */
    @Transactional
    public Map<String, Object> open(Long studentId, Long courseId, Integer round) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (studentId == null || courseId == null) { out.put("created", false); return out; }
        int r = round == null ? enrollmentService.getSystemConfig().getCurrentRound() : round;
        Optional<DropRequest> exists = repo
                .findFirstByStudentIdAndCourseIdAndRoundNoAndStatusOrderByIdDesc(studentId, courseId, r, "pending");
        if (exists.isPresent()) {
            out.put("created", false);
            out.put("id", exists.get().getId());
            return out;
        }
        Course c = courseRepo.findById(courseId).orElse(null);
        User stu = userRepo.findById(studentId).orElse(null);
        DropRequest d = new DropRequest();
        d.setStudentId(studentId);
        d.setCourseId(courseId);
        d.setRoundNo(r);
        d.setTeacherId(c == null ? null : c.getTeacherId());
        d.setStudentNo(stu == null ? null : stu.getNo());
        d.setStudentName(stu == null ? ("学生#" + studentId) : stu.getName());
        d.setCourseName(c == null ? ("课程#" + courseId) : c.getName());
        d.setReason("轮次结束后申请退课");
        d.setStatus("pending");
        d.setCreatedAt(LocalDateTime.now());
        repo.save(d);
        if (d.getTeacherId() != null) {
            notificationService.notify(d.getTeacherId(), "drop_request", "退课申请待处理",
                    d.getStudentName() + " 申请退选《" + d.getCourseName() + "》，请到「退课申请」处理", d.getId());
        }
        out.put("created", true);
        out.put("id", d.getId());
        return out;
    }

    /** 老师看自己课程的申请；管理员看全部（含未指定任课老师的申请）。学生已撤回的不显示。 */
    public List<Map<String, Object>> listForTeacher(Long teacherId, boolean admin) {
        List<DropRequest> list = admin
                ? repo.findAllByOrderByCreatedAtDesc()
                : repo.findByTeacherIdOrderByCreatedAtDesc(teacherId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DropRequest d : list) {
            if ("cancelled".equals(d.getStatus())) continue;   // 学生已撤回：教师端待处理列表不再显示
            out.add(row(d));
        }
        return out;
    }

    /** 学生撤回自己的待处理退课申请：状态置 cancelled，教师端同步消失，并站内通知教师 */
    @Transactional
    public Map<String, Object> cancel(Long id, Long studentId) {
        Map<String, Object> out = new LinkedHashMap<>();
        DropRequest d = id == null ? null : repo.findById(id).orElse(null);
        if (d == null || !d.getStudentId().equals(studentId)) {
            out.put("success", false); out.put("msg", "申请不存在或无权操作"); return out;
        }
        if (!"pending".equals(d.getStatus())) {
            out.put("success", false); out.put("msg", "该申请已被处理或已撤回，无法再次撤回"); return out;
        }
        d.setStatus("cancelled");
        repo.save(d);
        if (d.getTeacherId() != null) {
            notificationService.notify(d.getTeacherId(), "drop_request", "退课申请已撤回",
                    d.getStudentName() + " 已撤回《" + d.getCourseName() + "》的退课申请", d.getId());
        }
        out.put("success", true);
        out.put("msg", "已撤回退课申请，课程恢复正常选课状态");
        return out;
    }

    public List<Map<String, Object>> listForStudent(Long studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DropRequest d : repo.findByStudentIdOrderByCreatedAtDesc(studentId)) out.add(row(d));
        return out;
    }

    /** 同意：执行真实退课（跳过轮次限制）+ 站内通知学生 */
    @Transactional
    public Map<String, Object> approve(Long id, Long operatorId, boolean admin, String remark) {
        DropRequest d = repo.findById(id).orElse(null);
        if (d == null) return fail("申请不存在");
        if (!admin && !Objects.equals(d.getTeacherId(), operatorId)) return fail("无权处理该申请");
        if (!"pending".equals(d.getStatus())) return fail("该申请已处理");
        Map<String, Object> r = enrollmentService.dropByTeacher(d.getStudentId(), d.getCourseId(), d.getRoundNo());
        if (!Boolean.TRUE.equals(r.get("success"))) return fail(String.valueOf(r.get("msg")));
        d.setStatus("approved");
        d.setRemark(remark);
        d.setHandledAt(LocalDateTime.now());
        d.setHandledBy(operatorId);
        repo.save(d);
        notificationService.notify(d.getStudentId(), "drop_approved", "退课申请已通过",
                "《" + d.getCourseName() + "》已由任课老师同意退课，可在「我的课程」查看", d.getId());
        return ok("已同意退课", row(d));
    }

    /** 驳回：不改选课记录，只更新申请并通知学生 */
    @Transactional
    public Map<String, Object> reject(Long id, Long operatorId, boolean admin, String remark) {
        DropRequest d = repo.findById(id).orElse(null);
        if (d == null) return fail("申请不存在");
        if (!admin && !Objects.equals(d.getTeacherId(), operatorId)) return fail("无权处理该申请");
        if (!"pending".equals(d.getStatus())) return fail("该申请已处理");
        d.setStatus("rejected");
        d.setRemark(remark);
        d.setHandledAt(LocalDateTime.now());
        d.setHandledBy(operatorId);
        repo.save(d);
        notificationService.notify(d.getStudentId(), "drop_rejected", "退课申请被驳回",
                "《" + d.getCourseName() + "》的退课申请未通过" + (remark == null || remark.isBlank() ? "" : "：" + remark), d.getId());
        return ok("已驳回申请", row(d));
    }

    private Map<String, Object> row(DropRequest d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("studentId", d.getStudentId());
        m.put("studentNo", d.getStudentNo());
        m.put("studentName", d.getStudentName());
        m.put("courseId", d.getCourseId());
        m.put("courseName", d.getCourseName());
        m.put("teacherId", d.getTeacherId());
        m.put("roundNo", d.getRoundNo());
        m.put("status", d.getStatus());
        m.put("reason", d.getReason());
        m.put("remark", d.getRemark());
        m.put("createdAt", ts(d.getCreatedAt()));
        m.put("handledAt", ts(d.getHandledAt()));
        return m;
    }

    private long ts(LocalDateTime t) { return t == null ? 0L : java.sql.Timestamp.valueOf(t).getTime(); }

    private Map<String, Object> ok(String msg, Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true); m.put("msg", msg); m.put("data", data);
        return m;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false); m.put("msg", msg);
        return m;
    }
}
