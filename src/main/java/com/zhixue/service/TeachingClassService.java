package com.zhixue.service;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 教学班（组班重修）：教务一键组班 → 真正建班并排课 → 学生课表出现该班课程。
 * 与「跟班重修」的区别：跟班插到现有课程记录里，组班这里产出独立的教学班实体。
 */
@Service
public class TeachingClassService {

    @Autowired private TeachingClassRepository classRepo;
    @Autowired private RetakeApplyRepository retakeRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private GradeService gradeService;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private EnrollmentService enrollmentService;

    private static final Set<String> FINISHED = Set.of("PASSED", "FAILED", "CANCELLED");

    /**
     * 一键组班：把该课程「已报名且未进班」的重修学生收进教学班。
     * 同课程同开课学期已有开班中的教学班时直接追加，避免重复开班。
     */
    @Transactional
    public Map<String, Object> groupRetakes(Map<String, Object> body) {
        Long courseId = toLong(body.get("courseId"));
        if (courseId == null) throw new IllegalStateException("请选择要组班的课程");
        Course course = courseRepo.findById(courseId).orElseThrow(() -> new IllegalStateException("课程不存在"));

        List<RetakeApply> members = retakeRepo.findAll().stream()
                .filter(r -> courseId.equals(r.getCourseId()))
                .filter(r -> !FINISHED.contains(r.getStatus()))
                .filter(r -> r.getTeachingClassId() == null)
                .filter(r -> "APPLIED".equals(r.getStatus()) || "APPROVED".equals(r.getStatus())
                        || "GROUP".equals(r.getForm()))
                .sorted(Comparator.comparing(RetakeApply::getId))
                .toList();
        if (members.isEmpty()) throw new IllegalStateException("该课程暂无已报名的重修学生，无法开班");

        Integer semesterNo = toInt(body.get("semesterNo"));
        if (semesterNo == null) semesterNo = currentSemesterOf(members.get(0).getStudentId());

        TeachingClass tc = classRepo
                .findFirstByCourseIdAndSemesterNoAndStatusOrderByIdDesc(courseId, semesterNo, "OPEN")
                .orElse(null);
        boolean created = false;
        if (tc == null) {
            tc = new TeachingClass();
            tc.setCourseId(courseId);
            tc.setSemesterNo(semesterNo);
            tc.setClassNo(nextClassNo(course, semesterNo));
            tc.setTeacherId(toLong(body.get("teacherId")) != null
                    ? toLong(body.get("teacherId")) : course.getTeacherId());
            tc.setTime(blankToNull(body.get("time")) != null
                    ? blankToNull(body.get("time")) : course.getTime());
            tc.setLocation(blankToNull(body.get("location")) != null
                    ? blankToNull(body.get("location")) : course.getLocation());
            tc.setCampus(blankToNull(body.get("campus")) != null
                    ? blankToNull(body.get("campus")) : course.getCampus());
            tc.setCapacity(toInt(body.get("capacity")) != null
                    ? toInt(body.get("capacity")) : Math.max(30, members.size()));
            tc.setStatus("OPEN");
            tc.setSource("RETAKE");
            tc.setCreatedAt(LocalDateTime.now());
            tc = classRepo.save(tc);
            created = true;
        } else {
            // 已有开班中的教学班：本次只做补录，同时允许顺手改排课信息
            if (blankToNull(body.get("time")) != null) tc.setTime(blankToNull(body.get("time")));
            if (blankToNull(body.get("location")) != null) tc.setLocation(blankToNull(body.get("location")));
            if (blankToNull(body.get("campus")) != null) tc.setCampus(blankToNull(body.get("campus")));
            if (toLong(body.get("teacherId")) != null) tc.setTeacherId(toLong(body.get("teacherId")));
            if (toInt(body.get("capacity")) != null) tc.setCapacity(toInt(body.get("capacity")));
            tc = classRepo.save(tc);
        }
        if (tc.getCapacity() != null && members.size() > tc.getCapacity()) {
            throw new IllegalStateException("教学班容量不足：容量 " + tc.getCapacity() + "，本次需收 " + members.size() + " 人");
        }

        int round = currentRound();
        for (RetakeApply r : members) {
            r.setForm("GROUP");
            r.setTeachingClassId(tc.getId());
            r.setTargetClassId(null);
            r.setStatus("ENROLLED");
            if (r.getSemesterNo() == null) r.setSemesterNo(semesterNo);
            retakeRepo.save(r);

            // 写入报名记录 → 课表/我的课程/成绩都能看到该课程（0 积分，不占普通选课容量）
            Enrollment e = enrollmentRepo
                    .findByStudentIdAndCourseIdAndRound(r.getStudentId(), courseId, round)
                    .orElseGet(Enrollment::new);
            e.setStudentId(r.getStudentId());
            e.setCourseId(courseId);
            e.setRound(round);
            e.setPoints(0);
            e.setStatus("approved");
            e.setSemesterNo(semesterNo);
            e.setSource("retake_group");
            e.setTeachingClassId(tc.getId());
            enrollmentRepo.save(e);
        }

        Map<String, Object> out = classRow(tc);
        out.put("created", created);
        out.put("added", members.size());
        out.put("message", (created ? "已开班并排课" : "已追加到现有教学班") + "：" + tc.getClassNo()
                + " · " + members.size() + " 人");
        return out;
    }

    public List<Map<String, Object>> listClasses(Long courseId, Integer semesterNo, String status, String source, Long teacherId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TeachingClass tc : classRepo.findAllByOrderByIdDesc()) {
            if (courseId != null && !courseId.equals(tc.getCourseId())) continue;
            if (semesterNo != null && !semesterNo.equals(tc.getSemesterNo())) continue;
            if (status != null && !status.isBlank() && !status.equals(tc.getStatus())) continue;
            if (source != null && !source.isBlank() && !source.equals(tc.getSource())) continue;
            if (teacherId != null && !teacherId.equals(tc.getTeacherId())) continue;
            out.add(classRow(tc));
        }
        return out;
    }

    /** 排课：调整上课时间/地点/教师/容量（已进班学生的课表随之更新） */
    @Transactional
    public Map<String, Object> scheduleClass(Long id, Map<String, Object> body) {
        TeachingClass tc = classRepo.findById(id).orElseThrow(() -> new IllegalStateException("教学班不存在"));
        if ("CLOSED".equals(tc.getStatus())) throw new IllegalStateException("教学班已停开，不能再排课");
        if (body.containsKey("time")) tc.setTime(blankToNull(body.get("time")));
        if (body.containsKey("location")) tc.setLocation(blankToNull(body.get("location")));
        if (body.containsKey("campus")) tc.setCampus(blankToNull(body.get("campus")));
        if (body.containsKey("teacherId")) tc.setTeacherId(toLong(body.get("teacherId")));
        Integer cap = toInt(body.get("capacity"));
        if (cap != null) tc.setCapacity(cap);
        if (blankToNull(body.get("note")) != null) tc.setNote(blankToNull(body.get("note")));
        classRepo.save(tc);
        return classRow(tc);
    }

    /** 停开：班级置 CLOSED，撤掉自动写入的重修报名，学生回到「已同意」可重新定形式 */
    @Transactional
    public Map<String, Object> closeClass(Long id) {
        TeachingClass tc = classRepo.findById(id).orElseThrow(() -> new IllegalStateException("教学班不存在"));
        if ("CLOSED".equals(tc.getStatus())) throw new IllegalStateException("教学班已停开");
        List<RetakeApply> members = retakeRepo.findByTeachingClassIdOrderByIdAsc(id);
        for (RetakeApply r : members) {
            r.setTeachingClassId(null);
            r.setStatus("APPROVED");
            retakeRepo.save(r);
            enrollmentRepo.findByStudentId(r.getStudentId()).stream()
                    .filter(e -> id.equals(e.getTeachingClassId()))
                    .forEach(e -> {
                        e.setStatus("dropped");
                        e.setTeachingClassId(null);
                        enrollmentRepo.save(e);
                    });
        }
        tc.setStatus("CLOSED");
        classRepo.save(tc);
        Map<String, Object> out = classRow(tc);
        out.put("released", members.size());
        return out;
    }

    /** 教学班名单：重修班=重修学生+状态；方案教学班=挂班在读学生 */
    public List<Map<String, Object>> members(Long id) {
        TeachingClass tc = classRepo.findById(id).orElseThrow(() -> new IllegalStateException("教学班不存在"));
        if ("PLAN".equals(tc.getSource())) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Enrollment e : enrollmentRepo.findByTeachingClassIdOrderByIdAsc(tc.getId())) {
                User u = userRepo.findById(e.getStudentId()).orElse(null);
                Grade g = gradeService.findByStudentAndCourse(e.getStudentId(), tc.getCourseId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("studentId", e.getStudentId());
                m.put("studentNo", u != null ? u.getNo() : "");
                m.put("studentName", u != null ? u.getName() : "未知");
                m.put("studentMajor", u != null ? u.getMajor() : "");
                m.put("studentClass", u != null ? u.getClassNo() : "");
                m.put("studentGrade", u != null ? u.getGrade() : null);
                m.put("status", e.getStatus());
                m.put("statusText", "approved".equals(e.getStatus()) ? "在读" : e.getStatus());
                m.put("points", e.getPoints());
                m.put("courseId", tc.getCourseId());
                m.put("totalScore", g != null ? g.getTotalScore() : null);
                out.add(m);
            }
            return out;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (RetakeApply r : retakeRepo.findByTeachingClassIdOrderByIdAsc(tc.getId())) {
            User u = userRepo.findById(r.getStudentId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("retakeId", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("studentNo", u != null ? u.getNo() : "");
            m.put("studentName", u != null ? u.getName() : "未知");
            m.put("studentClass", u != null ? u.getClassNo() : "");
            m.put("status", r.getStatus());
            m.put("statusText", MakeupRetakeService.statusCn(r.getStatus()));
            m.put("score", r.getScore());
            m.put("attemptNo", r.getAttemptNo());
            out.add(m);
        }
        return out;
    }

    /* ==================== 内部工具 ==================== */

    private Map<String, Object> classRow(TeachingClass tc) {
        Course c = courseRepo.findById(tc.getCourseId()).orElse(null);
        User t = tc.getTeacherId() == null ? null : userRepo.findById(tc.getTeacherId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tc.getId());
        m.put("classNo", tc.getClassNo());
        m.put("courseId", tc.getCourseId());
        m.put("courseName", c != null ? c.getName() : "（课程已删除）");
        m.put("credit", c != null ? c.getCredit() : null);
        m.put("semesterNo", tc.getSemesterNo());
        m.put("teacherId", tc.getTeacherId());
        m.put("teacherName", t != null ? t.getName() : "");
        m.put("time", tc.getTime());
        m.put("location", tc.getLocation());
        m.put("campus", tc.getCampus());
        m.put("capacity", tc.getCapacity());
        // 方案教学班的成员=选课记录挂班;重修班的成员=重修申请
        m.put("memberCount", "PLAN".equals(tc.getSource())
                ? enrollmentRepo.countByTeachingClassId(tc.getId())
                : retakeRepo.countByTeachingClassId(tc.getId()));
        m.put("status", tc.getStatus());
        m.put("statusText", "OPEN".equals(tc.getStatus()) ? "开班中" : "已停开");
        m.put("note", tc.getNote());
        m.put("createdAt", tc.getCreatedAt());
        return m;
    }

    /** 教学班号：RX-课程代码-学期-序号，如 RX-CS103-4-01 */
    private String nextClassNo(Course course, Integer semesterNo) {
        String code = course.getCourseCode() != null && !course.getCourseCode().isBlank()
                ? course.getCourseCode() : ("C" + course.getId());
        String base = "RX-" + code + "-" + (semesterNo == null ? "0" : semesterNo);
        int n = 1;
        while (classRepo.existsByClassNo(base + "-" + String.format("%02d", n))) n++;
        return base + "-" + String.format("%02d", n);
    }

    private int currentSemesterOf(Long studentId) {
        User u = studentId == null ? null : userRepo.findById(studentId).orElse(null);
        SystemConfig cfg = sysRepo.findById(1L).orElseGet(SystemConfig::new);
        int off = cfg.getSemesterOffset() == null ? 0 : cfg.getSemesterOffset();
        return GraduationService.currentSemesterNo(u != null ? u.getGrade() : null, u != null ? u.getMajor() : null, LocalDate.now(), off);
    }

    private int currentRound() {
        Integer r = enrollmentService.getSystemConfig().getCurrentRound();
        return r == null ? 1 : r;
    }

    private static Long toLong(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try { return Long.valueOf(o.toString().trim()); } catch (Exception e) { return null; }
    }

    private static Integer toInt(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try { return Integer.valueOf(o.toString().trim()); } catch (Exception e) { return null; }
    }

    private static String blankToNull(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
