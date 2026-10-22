package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.CourseIntent;
import com.zhixue.repository.CourseIntentRepository;
import com.zhixue.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 预选意向单业务 */
@Service
public class IntentService {

    @Autowired private CourseIntentRepository intentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private CourseService courseService;
    @Autowired private EnrollmentService enrollmentService;

    private Map<String, Object> row(CourseIntent it) {
        Course c = courseRepo.findById(it.getCourseId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intentId", it.getId());
        m.put("courseId", it.getCourseId());
        m.put("courseName", c != null ? c.getName() : "（课程已删除）");
        m.put("category", c != null ? c.getCategory() : "");
        m.put("credit", c != null ? c.getCredit() : 0);
        m.put("teacherName", c != null ? courseService.teacherName(c.getTeacherId()) : "");
        m.put("time", c != null ? c.getTime() : "");
        m.put("location", c != null ? c.getLocation() : "");
        m.put("campus", c != null ? c.getCampus() : "");
        m.put("capacity", c != null ? c.getCapacity() : 0);
        m.put("desiredPoints", it.getDesiredPoints());
        m.put("note", it.getNote());
        m.put("createdAt", it.getCreatedAt() != null ? java.sql.Timestamp.valueOf(it.getCreatedAt()).getTime() : null);
        return m;
    }

    public List<Map<String, Object>> list(Long studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CourseIntent it : intentRepo.findByStudentIdOrderByCreatedAtDesc(studentId)) out.add(row(it));
        return out;
    }

    public long count(Long studentId) { return intentRepo.countByStudentId(studentId); }

    /** 学生是否已把某课程加入意向单 */
    public CourseIntent find(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) return null;
        return intentRepo.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
    }

    public int totalPoints(Long studentId) {
        int sum = 0;
        for (CourseIntent it : intentRepo.findByStudentIdOrderByCreatedAtAsc(studentId)) {
            sum += (it.getDesiredPoints() != null && it.getDesiredPoints() >= 0) ? it.getDesiredPoints() : 10;
        }
        return sum;
    }

    @Transactional
    public Map<String, Object> add(Long studentId, Long courseId, Integer points, String note) {
        Map<String, Object> r = new HashMap<>();
        Course c = courseRepo.findById(courseId).orElse(null);
        if (c == null) { r.put("success", false); r.put("msg", "课程不存在"); return r; }
        // 必修课由系统自动安排进课表，不允许加入意向单
        if (enrollmentService.isRequiredCourse(courseId)) {
            r.put("success", false);
            r.put("msg", "必修课已自动安排进课表，无需选课");
            return r;
        }
        CourseIntent it = intentRepo.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
        boolean isNew = it == null;
        if (isNew) {
            it = new CourseIntent();
            it.setStudentId(studentId);
            it.setCourseId(courseId);
            it.setCreatedAt(LocalDateTime.now());
        }
        // 0-69 分自由投入：0 分表示暂不投入积分（未传值时才默认 10 分）
        it.setDesiredPoints(points != null && points >= 0 ? points : 10);
        it.setNote(note);
        intentRepo.save(it);
        r.put("success", true);
        r.put("msg", (isNew ? "已加入意向单：" : "已更新意向：" ) + c.getName());
        r.put("count", count(studentId));
        return r;
    }

    @Transactional
    public Map<String, Object> remove(Long studentId, Long courseId) {
        Map<String, Object> r = new HashMap<>();
        CourseIntent it = intentRepo.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
        if (it == null) { r.put("success", false); r.put("msg", "意向单中没有该课程"); return r; }
        intentRepo.delete(it);
        r.put("success", true);
        r.put("msg", "已从意向单移除");
        r.put("count", count(studentId));
        return r;
    }

    @Transactional
    public Map<String, Object> clear(Long studentId) {
        List<CourseIntent> list = intentRepo.findByStudentIdOrderByCreatedAtAsc(studentId);
        intentRepo.deleteAll(list);
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("msg", "已清空意向单（" + list.size() + " 门）");
        r.put("count", 0);
        return r;
    }

    /** 单门意向一键转为正式选课（进入待审批） */
    @Transactional
    public Map<String, Object> convertOne(Long studentId, Long courseId) {
        CourseIntent it = intentRepo.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
        if (it == null) {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false); r.put("msg", "意向单中没有该课程"); return r;
        }
        int pts = (it.getDesiredPoints() != null && it.getDesiredPoints() >= 0) ? it.getDesiredPoints() : 10;
        Map<String, Object> res = enrollmentService.enroll(studentId, courseId, pts);
        if (Boolean.TRUE.equals(res.get("success"))) {
            intentRepo.delete(it);
            res.put("msg", "已转为正式选课（待审批）：" + res.get("msg"));
        }
        return res;
    }

    /** 一键提交：按加入顺序逐门选课，返回明细 */
    @Transactional
    public Map<String, Object> submitAll(Long studentId) {
        List<CourseIntent> list = intentRepo.findByStudentIdOrderByCreatedAtAsc(studentId);
        List<Map<String, Object>> details = new ArrayList<>();
        int ok = 0, fail = 0;
        for (CourseIntent it : list) {
            int pts = (it.getDesiredPoints() != null && it.getDesiredPoints() >= 0) ? it.getDesiredPoints() : 10;
            Map<String, Object> res = enrollmentService.enroll(studentId, it.getCourseId(), pts);
            boolean success = Boolean.TRUE.equals(res.get("success"));
            Course c = courseRepo.findById(it.getCourseId()).orElse(null);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("courseId", it.getCourseId());
            d.put("courseName", c != null ? c.getName() : "?");
            d.put("points", pts);
            d.put("success", success);
            d.put("msg", res.get("msg"));
            details.add(d);
            if (success) { ok++; intentRepo.delete(it); } else { fail++; }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("msg", list.isEmpty() ? "意向单是空的，请先收藏课程"
                : "一键提交完成：成功 " + ok + " 门" + (fail > 0 ? "，失败 " + fail + " 门" : "，全部成功"));
        Map<String, Object> data = new HashMap<>();
        data.put("okCount", ok);
        data.put("failCount", fail);
        data.put("details", details);
        r.put("data", data);
        return r;
    }
}
