package com.zhixue.service;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EnrollmentService {

    public static final int POINTS_PER_ROUND = 60;

    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private WaitlistService waitlistService;
    @Autowired private NotificationService notificationService;

    public SystemConfig getSystemConfig() {
        SystemConfig c = sysRepo.findById(1L).orElseGet(() -> sysRepo.save(new SystemConfig()));
        if (c.getConfirmMinutes() == null) { c.setConfirmMinutes(30); c = sysRepo.save(c); }
        return c;
    }

    // 学生在某轮已用积分：已报名(pending) + 已录取(approved) 之和
    // 报名即冻结积分，未录取时自动退还，保证"积分竞价"公平
    public int getUsedPoints(Long studentId, int round) {
        return enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus()) || "pending".equals(e.getStatus()))
                .mapToInt(Enrollment::getPoints)
                .sum();
    }

    public int getRemainingPoints(Long studentId, int round) {
        return POINTS_PER_ROUND - getUsedPoints(studentId, round);
    }

    public Enrollment getEnrollment(Long studentId, Long courseId, int round) {
        return enrollmentRepo.findByStudentIdAndCourseIdAndRound(studentId, courseId, round).orElse(null);
    }

    /** 所有必修课：由教学计划既定，系统自动安排，不走选课流程 */
    public List<Course> requiredCourses() {
        return courseRepo.findAll().stream()
                .filter(c -> c.getCategory() != null && c.getCategory().contains("必修"))
                .sorted(Comparator.comparing(Course::getId))
                .toList();
    }

    public boolean isRequiredCourse(Long courseId) {
        Course c = courseId == null ? null : courseRepo.findById(courseId).orElse(null);
        return c != null && c.getCategory() != null && c.getCategory().contains("必修");
    }

    /** 必修课自动安排：为学生补齐必修课记录（不占积分、直接录取）。幂等，可重复调用 */
    @Transactional
    public int ensureRequiredEnrollments(Long studentId) {
        if (studentId == null) return 0;
        int round = getSystemConfig().getCurrentRound();
        int added = 0;
        for (Course c : requiredCourses()) {
            Enrollment exist = getEnrollment(studentId, c.getId(), round);
            if (exist == null) {
                Enrollment e = new Enrollment();
                e.setStudentId(studentId);
                e.setCourseId(c.getId());
                e.setRound(round);
                e.setPoints(0);
                e.setStatus("approved");
                enrollmentRepo.save(e);
                added++;
            } else if (!"approved".equals(exist.getStatus())) {
                exist.setStatus("approved");
                exist.setPoints(0);
                enrollmentRepo.save(exist);
            }
        }
        return added;
    }

    // 课程报名统计
    public Map<String, Integer> courseStat(Long courseId, int round) {
        List<Enrollment> list = enrollmentRepo.findByCourseIdAndRound(courseId, round);
        int total = (int) list.stream().filter(e -> !"dropped".equals(e.getStatus())).count();
        int pending = (int) list.stream().filter(e -> "pending".equals(e.getStatus())).count();
        int approved = (int) list.stream().filter(e -> "approved".equals(e.getStatus())).count();
        int rejected = (int) list.stream().filter(e -> "rejected".equals(e.getStatus())).count();
        Map<String, Integer> m = new HashMap<>();
        m.put("total", total); m.put("pending", pending);
        m.put("approved", approved); m.put("rejected", rejected);
        return m;
    }

    public boolean isOversubscribed(Long courseId, int round) {
        Course c = courseRepo.findById(courseId).orElse(null);
        if (c == null) return false;
        Map<String, Integer> stat = courseStat(courseId, round);
        return stat.get("total") > c.getCapacity();
    }

    @Transactional
    public Map<String, Object> enroll(Long studentId, Long courseId, int points) {
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();
        Map<String, Object> result = new HashMap<>();

        if (!sys.getRoundOpen()) {
            result.put("success", false); result.put("msg", "当前选课已关闭"); return result;
        }
        Course course = courseRepo.findById(courseId).orElse(null);
        if (course == null) { result.put("success", false); result.put("msg", "课程不存在"); return result; }

        if (isRequiredCourse(courseId)) {
            result.put("success", false); result.put("msg", "必修课已自动安排进课表，无需选课"); return result;
        }

        // 已报名检查
        Enrollment existing = getEnrollment(studentId, courseId, round);
        if (existing != null && !"rejected".equals(existing.getStatus()) && !"dropped".equals(existing.getStatus())) {
            result.put("success", false); result.put("msg", "你已报名该课程"); return result;
        }

        // 积分检查
        int remaining = getRemainingPoints(studentId, round);
        if (points < 1) { result.put("success", false); result.put("msg", "投入积分至少为1"); return result; }
        if (points > remaining) { result.put("success", false); result.put("msg", "积分不足，本轮剩余 " + remaining + " 分"); return result; }

        // 时间冲突检查（与已录取课程）
        List<Enrollment> myApproved = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus())).toList();
        for (Enrollment e : myApproved) {
            Course ec = courseRepo.findById(e.getCourseId()).orElse(null);
            if (ec != null && hasTimeConflict(course, ec)) {
                result.put("success", false); result.put("msg", "与已录取课程时间冲突"); return result;
            }
        }


        // 每学期学分上限
        if (!isRequiredCourse(courseId)) {
            Map<String, Integer> stat = courseStat(courseId, round);
            // 学生当前已 approved 的学分累加
            int myCredit = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                    .filter(e -> "approved".equals(e.getStatus()))
                    .map(e -> courseRepo.findById(e.getCourseId()).map(Course::getCredit).orElse(0))
                    .mapToInt(x -> x == null ? 0 : x).sum();
            int courseCredit = course.getCredit() == null ? 0 : course.getCredit();
            Integer maxC = sys.getMaxCreditPerSemester();
            if (maxC != null && maxC > 0 && myCredit + courseCredit > maxC) {
                result.put("success", false);
                result.put("msg", "超出本学期学分上限 " + maxC + " 分，当前已修 " + myCredit + " 分，本课后 " + (myCredit + courseCredit) + " 分");
                return result;
            }
        }

        // 删除之前被拒绝/退选的记录
        if (existing != null) enrollmentRepo.delete(existing);

        Enrollment enr = new Enrollment();
        enr.setStudentId(studentId);
        enr.setCourseId(courseId);
        enr.setRound(round);
        enr.setPoints(points);
        enr.setStatus("pending");
        enrollmentRepo.save(enr);

        // 选课期间只登记报名，本轮结算时按投入积分统一录取
        Map<String, Integer> stat = courseStat(courseId, round);
        int total = stat.get("total");
        int cap = course.getCapacity() == null ? 0 : course.getCapacity();
        result.put("success", true);
        result.put("msg", "报名成功！已投入 " + points + " 积分，当前报名 " + total + "/" + cap + " 人。"
                + (total > cap
                    ? "该课程已超额，本轮结束后按投入积分从高到低录取，积分越高越有利"
                    : "本轮结束后统一录取"));
        return result;
    }

    @Transactional
    public Map<String, Object> drop(Long studentId, Long courseId) {
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();
        Map<String, Object> result = new HashMap<>();
        if (isRequiredCourse(courseId)) {
            result.put("success", false);
            result.put("msg", "必修课由教学计划自动安排，不能退课");
            return result;
        }
        Enrollment enr = getEnrollment(studentId, courseId, round);
        if (enr == null) { result.put("success", false); result.put("msg", "未找到选课记录"); return result; }
        if ("rejected".equals(enr.getStatus()) || "dropped".equals(enr.getStatus())) {
            result.put("success", false); result.put("msg", "该课程未录取"); return result;
        }
        String wasStatus = enr.getStatus();
        enr.setStatus("dropped");
        enrollmentRepo.save(enr);
        // 已录取课程被退 → 释放名额并自动递补候补队列下一位
        if ("approved".equals(wasStatus)) waitlistService.promoteIfNeeded(courseId, round);
        result.put("success", true);
        result.put("msg", "pending".equals(wasStatus) ? "已撤销报名，积分已退还" : "退课成功，积分已退还");
        return result;
    }

    /** 改选：退掉旧课（释放名额与积分）并报名新课；若配置改选惩罚，惩罚积分计入新课投入（从总积分中消耗） */
    @Transactional
    public Map<String, Object> reselect(Long studentId, Long oldCourseId, Long newCourseId, int points) {
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();
        Map<String, Object> result = new LinkedHashMap<>();
        if (!sys.getRoundOpen()) {
            result.put("success", false); result.put("msg", "当前轮次未开放，无法改选"); return result;
        }
        if (oldCourseId.equals(newCourseId)) {
            result.put("success", false); result.put("msg", "请选择与原课程不同的课程"); return result;
        }
        if (isRequiredCourse(oldCourseId)) {
            result.put("success", false); result.put("msg", "必修课由教学计划自动安排，不能改选"); return result;
        }
        Enrollment old = getEnrollment(studentId, oldCourseId, round);
        if (old == null || "dropped".equals(old.getStatus()) || "rejected".equals(old.getStatus())) {
            result.put("success", false); result.put("msg", "原课程状态不可改选"); return result;
        }
        Course nc = courseRepo.findById(newCourseId).orElse(null);
        if (nc == null) { result.put("success", false); result.put("msg", "新课程不存在"); return result; }
        if (isRequiredCourse(newCourseId)) {
            result.put("success", false); result.put("msg", "必修课由教学计划自动安排，无需选课"); return result;
        }
        Enrollment existNew = getEnrollment(studentId, newCourseId, round);
        if (existNew != null && !"rejected".equals(existNew.getStatus()) && !"dropped".equals(existNew.getStatus())) {
            result.put("success", false); result.put("msg", "你已报名该课程，无需改选"); return result;
        }
        int penalty = sys.getReselectPenalty() == null ? 0 : sys.getReselectPenalty();
        int effective = points + penalty;
        String oldStatus = old.getStatus();
        // 先退旧课（释放名额与积分）
        old.setStatus("dropped");
        enrollmentRepo.save(old);
        if ("approved".equals(oldStatus)) waitlistService.promoteIfNeeded(oldCourseId, round);
        // 报名新课（惩罚积分计入新课投入，从总积分中消耗）
        Map<String, Object> enr = enroll(studentId, newCourseId, effective);
        if (!(Boolean) enr.get("success")) {
            Enrollment rollback = getEnrollment(studentId, oldCourseId, round);
            if (rollback != null) { rollback.setStatus(oldStatus); enrollmentRepo.save(rollback); }
            result.put("success", false);
            result.put("msg", (String) enr.get("msg"));
            return result;
        }
        result.put("success", true);
        result.put("penalty", penalty);
        String oldName = courseRepo.findById(oldCourseId).map(Course::getName).orElse("原课程");
        result.put("msg", "改选成功！已退掉《" + oldName + "》，改报《" + nc.getName() + "》"
                + (penalty > 0 ? "，本次改选消耗 " + penalty + " 积分惩罚" : ""));
        return result;
    }

    // 老师审批
    @Transactional
    public Map<String, Object> approve(Long courseId, List<Long> selectedStudentIds) {
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();
        Map<String, Object> result = new HashMap<>();
        Course course = courseRepo.findById(courseId).orElse(null);
        if (course == null) { result.put("success", false); result.put("msg", "课程不存在"); return result; }
        if (selectedStudentIds.size() > course.getCapacity()) {
            result.put("success", false); result.put("msg", "录取人数不能超过容量 " + course.getCapacity()); return result;
        }
        List<Enrollment> list = enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> !"dropped".equals(e.getStatus())).toList();
        for (Enrollment e : list) {
            if (selectedStudentIds.contains(e.getStudentId())) {
                if (!"approved".equals(e.getStatus())) notifyResult(e, course, true);
                e.setStatus("approved");
            } else if ("approved".equals(e.getStatus()) || "pending".equals(e.getStatus())) {
                notifyResult(e, course, false);
                e.setStatus("rejected");
            }
        }
        enrollmentRepo.saveAll(list);
        result.put("success", true);
        result.put("msg", "审批完成，已录取 " + selectedStudentIds.size() + " 人");
        return result;
    }

    /**
     * 结算某一轮选课：每门课按投入积分从高到低录取（同分先报名者优先），
     * 超出容量的记为未录取（积分自动退还）。必修课由教学计划自动安排，不参与结算。
     */
    @Transactional
    public Map<String, Object> settleRound(Integer round) {
        if (round == null) round = getSystemConfig().getCurrentRound();
        List<Map<String, Object>> details = new ArrayList<>();
        int admitted = 0, rejected = 0;
        for (Course c : courseRepo.findAll()) {
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            List<Enrollment> pending = enrollmentRepo.findByCourseIdAndRound(c.getId(), round).stream()
                    .filter(e -> "pending".equals(e.getStatus()))
                    .sorted((a, b) -> {
                        int d = Integer.compare(nz(b.getPoints()), nz(a.getPoints()));  // 积分降序
                        return d != 0 ? d : Long.compare(a.getId(), b.getId());        // 同分先报名优先
                    })
                    .toList();
            if (pending.isEmpty()) continue;
            int cap = c.getCapacity() == null ? 0 : c.getCapacity();
            for (int i = 0; i < pending.size(); i++) {
                Enrollment e = pending.get(i);
                boolean ok = i < cap;
                if (ok) { e.setStatus("approved"); admitted++; }
                else { e.setStatus("rejected"); rejected++; }
                notifyResult(e, c, ok);
            }
            enrollmentRepo.saveAll(pending);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("courseId", c.getId());
            d.put("courseName", c.getName());
            d.put("capacity", cap);
            d.put("applied", pending.size());
            d.put("admitted", Math.min(cap, pending.size()));
            d.put("cutline", pending.size() > cap ? nz(pending.get(cap - 1 < 0 ? 0 : cap - 1).getPoints()) : null);
            details.add(d);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("msg", "第 " + round + " 轮结算完成：录取 " + admitted + " 人次，未录取 " + rejected + " 人次（未录取积分已退还）");
        Map<String, Object> data = new HashMap<>();
        data.put("round", round);
        data.put("admitted", admitted);
        data.put("rejected", rejected);
        data.put("details", details);
        r.put("data", data);
        return r;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    /** 审批/结算结果 → 站内消息推送 */
    private void notifyResult(Enrollment e, Course c, boolean approved) {
        try {
            notificationService.notify(e.getStudentId(), "enroll",
                    approved ? "🎉 选课录取通知" : "选课结果通知",
                    "《" + c.getName() + "》" + (approved ? "已被录取，请按时上课" : "未被录取，所投积分已退还"),
                    e.getCourseId());
        } catch (Exception ignore) {}
    }

    // 时间冲突检测
    private boolean hasTimeConflict(Course a, Course b) {
        List<Map<String, String>> slotsA = parseTimeSlots(a.getTime());
        List<Map<String, String>> slotsB = parseTimeSlots(b.getTime());
        for (Map<String, String> sa : slotsA) {
            for (Map<String, String> sb : slotsB) {
                if (sa.get("day").equals(sb.get("day"))) {
                    int aS = toMin(sa.get("start")), aE = toMin(sa.get("end"));
                    int bS = toMin(sb.get("start")), bE = toMin(sb.get("end"));
                    if (aS < bE && bS < aE) return true;
                }
            }
        }
        return false;
    }

    private List<Map<String, String>> parseTimeSlots(String timeStr) {
        List<Map<String, String>> slots = new ArrayList<>();
        if (timeStr == null) return slots;
        String[] parts = timeStr.split("/");
        Pattern p = Pattern.compile("(周[一二三四五六日])\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})");
        for (String part : parts) {
            Matcher m = p.matcher(part.trim());
            if (m.find()) {
                Map<String, String> s = new HashMap<>();
                s.put("day", m.group(1)); s.put("start", m.group(2)); s.put("end", m.group(3));
                slots.add(s);
            }
        }
        return slots;
    }

    private int toMin(String t) {
        String[] p = t.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** 批量选课（一次投多门）。部分失败的返回明细，事务逐门处理 */
    @Transactional
    public Map<String, Object> enrollBatch(Long studentId, List<Long> courseIds, int pointsPerCourse) {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();
        int success = 0, fail = 0;
        for (Long cid : courseIds) {
            Map<String, Object> one = enroll(studentId, cid, pointsPerCourse);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", cid);
            item.put("success", one.get("success"));
            item.put("msg", one.get("msg"));
            details.add(item);
            if (Boolean.TRUE.equals(one.get("success"))) success++; else fail++;
        }
        r.put("success", fail == 0);
        r.put("msg", fail == 0 ? ("批量报名成功，共 " + success + " 门")
                              : ("部分失败：成功 " + success + " 门，失败 " + fail + " 门"));
        r.put("data", details);
        return r;
    }

    /** 批量退课 */
    @Transactional
    public Map<String, Object> dropBatch(Long studentId, List<Long> courseIds) {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();
        int success = 0, fail = 0;
        for (Long cid : courseIds) {
            Map<String, Object> one = drop(studentId, cid);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", cid);
            item.put("success", one.get("success"));
            item.put("msg", one.get("msg"));
            details.add(item);
            if (Boolean.TRUE.equals(one.get("success"))) success++; else fail++;
        }
        r.put("success", fail == 0);
        r.put("msg", fail == 0 ? ("批量退课成功，共 " + success + " 门")
                              : ("部分失败：成功 " + success + " 门，失败 " + fail + " 门"));
        r.put("data", details);
        return r;
    }
}
