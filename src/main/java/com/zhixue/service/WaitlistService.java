package com.zhixue.service;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WaitlistService {

    public static final List<String> ACTIVE = List.of("waiting", "confirming");

    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private NotificationService notificationService;

    public SystemConfig getConfig() {
        SystemConfig c = sysRepo.findById(1L).orElseGet(() -> sysRepo.save(new SystemConfig()));
        if (c.getConfirmMinutes() == null) { c.setConfirmMinutes(30); c = sysRepo.save(c); }
        return c;
    }

    public int confirmMinutes() {
        Integer m = getConfig().getConfirmMinutes();
        return m != null && m > 0 ? m : 30;
    }

    // 学生在某课程的进行中候补记录
    public Optional<Waitlist> findActive(Long studentId, Long courseId, int round) {
        return waitlistRepo.findByStudentIdAndCourseIdAndRound(studentId, courseId, round)
                .filter(w -> ACTIVE.contains(w.getStatus()));
    }

    // 某课程进行中的候补队列（按加入时间升序）
    public List<Waitlist> activeForCourse(Long courseId, int round) {
        return waitlistRepo.findByCourseIdAndRoundAndStatusInOrderByJoinTimeAsc(courseId, round, ACTIVE);
    }

    // 某学生在当前轮的候补记录
    public List<Waitlist> activeForStudent(Long studentId, int round) {
        return waitlistRepo.findByStudentIdAndRoundAndStatusInOrderByJoinTimeAsc(studentId, round, ACTIVE);
    }

    // 排队位置（1 起）
    public int position(Waitlist w) {
        return (int) activeForCourse(w.getCourseId(), w.getRound()).stream()
                .filter(x -> !x.getId().equals(w.getId()) && x.getJoinTime().isBefore(w.getJoinTime()))
                .count() + 1;
    }

    // 已占用名额 = 已录取 + 待确认（名额已预留给递补者）
    private int reserved(Long courseId, int round) {
        int approved = (int) enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> "approved".equals(e.getStatus())).count();
        long confirming = activeForCourse(courseId, round).stream()
                .filter(w -> "confirming".equals(w.getStatus())).count();
        return approved + (int) confirming;
    }

    // 课程是否满员（无可直接选课的空位）
    public boolean isFull(Long courseId, int round) {
        Course c = courseRepo.findById(courseId).orElse(null);
        if (c == null || c.getCapacity() == null) return false;
        return reserved(courseId, round) >= c.getCapacity();
    }

    // 加入候补队列
    @Transactional
    public Map<String, Object> join(Long studentId, Long courseId) {
        SystemConfig sys = getConfig();
        int round = sys.getCurrentRound();
        Map<String, Object> result = new HashMap<>();
        if (!sys.getRoundOpen()) { result.put("success", false); result.put("msg", "当前选课已关闭"); return result; }
        Course course = courseRepo.findById(courseId).orElse(null);
        if (course == null) { result.put("success", false); result.put("msg", "课程不存在"); return result; }
        Enrollment enr = enrollmentRepo.findByStudentIdAndCourseIdAndRound(studentId, courseId, round).orElse(null);
        if (enr != null && !"dropped".equals(enr.getStatus()) && !"rejected".equals(enr.getStatus())) {
            result.put("success", false); result.put("msg", "你已报名该课程，无需候补"); return result;
        }
        if (findActive(studentId, courseId, round).isPresent()) {
            result.put("success", false); result.put("msg", "你已在候补队列中"); return result;
        }
        if (!isFull(courseId, round)) {
            result.put("success", false); result.put("msg", "课程未满员，可直接报名选课"); return result;
        }
        Waitlist w = new Waitlist();
        w.setStudentId(studentId);
        w.setCourseId(courseId);
        w.setRound(round);
        w.setJoinTime(LocalDateTime.now());
        w.setStatus("waiting");
        waitlistRepo.save(w);
        int pos = position(w);
        result.put("success", true);
        result.put("msg", "已加入候补队列，当前排在第 " + pos + " 位");
        Map<String, Object> data = new HashMap<>();
        data.put("position", pos);
        result.put("data", data);
        return result;
    }

    // 退出候补
    @Transactional
    public Map<String, Object> cancel(Long studentId, Long courseId) {
        int round = getConfig().getCurrentRound();
        Waitlist w = findActive(studentId, courseId, round).orElse(null);
        if (w == null) return fail("未找到候补记录");
        String old = w.getStatus();
        w.setStatus("cancelled");
        waitlistRepo.save(w);
        if ("confirming".equals(old)) promoteIfNeeded(courseId, round);
        return ok("已退出候补队列");
    }

    // 确认录取（递补成功后的限时确认）
    @Transactional
    public Map<String, Object> confirm(Long waitlistId, Long studentId) {
        Waitlist w = waitlistRepo.findById(waitlistId).orElse(null);
        if (w == null || !w.getStudentId().equals(studentId)) return fail("候补记录不存在");
        if (!"confirming".equals(w.getStatus())) return fail("当前状态不可确认");
        if (w.getDeadline() == null || w.getDeadline().isBefore(LocalDateTime.now())) {
            w.setStatus("timeout");
            waitlistRepo.save(w);
            promoteIfNeeded(w.getCourseId(), w.getRound());
            return fail("确认超时，名额已释放给下一位");
        }
        Course course = courseRepo.findById(w.getCourseId()).orElse(null);
        if (course == null) return fail("课程不存在");
        if (hasTimeConflict(studentId, course, w.getRound())) {
            w.setStatus("timeout");
            waitlistRepo.save(w);
            promoteIfNeeded(w.getCourseId(), w.getRound());
            return fail("与其他已录取课程时间冲突，名额已释放");
        }
        enrollmentRepo.findByStudentIdAndCourseIdAndRound(studentId, w.getCourseId(), w.getRound())
                .ifPresent(enrollmentRepo::delete);
        Enrollment enr = new Enrollment();
        enr.setStudentId(studentId);
        enr.setCourseId(w.getCourseId());
        enr.setRound(w.getRound());
        enr.setPoints(1);
        enr.setStatus("approved");
        enrollmentRepo.save(enr);
        w.setStatus("confirmed");
        waitlistRepo.save(w);
        return ok("确认成功，已录取「" + course.getName() + "」");
    }

    // 递补：有空位时按队列顺序把 waiting 变为 confirming 并设置确认截止时间
    @Transactional
    public void promoteIfNeeded(Long courseId, int round) {
        Course course = courseRepo.findById(courseId).orElse(null);
        if (course == null || course.getCapacity() == null) return;
        int capacity = course.getCapacity();
        int reserved = reserved(courseId, round);
        List<Waitlist> waiting = activeForCourse(courseId, round).stream()
                .filter(w -> "waiting".equals(w.getStatus())).toList();
        for (Waitlist w : waiting) {
            if (reserved >= capacity) break;
            w.setStatus("confirming");
            w.setPromoteTime(LocalDateTime.now());
            w.setDeadline(LocalDateTime.now().plusMinutes(confirmMinutes()));
            waitlistRepo.save(w);
            notifyWaitConfirm(w, course);
            reserved++;
        }
    }

    // ===== 站内消息通知（SSE 实时推送） =====
    private void notifyWaitConfirm(Waitlist w, Course course) {
        try {
            notificationService.notify(w.getStudentId(), "waitlist_confirm", "🎉 递补成功，待确认",
                    "《" + course.getName() + "》已空出名额，请在 " + confirmMinutes() + " 分钟内确认录取，超时名额将释放",
                    w.getId());
        } catch (Exception ignore) {}
    }

    private void notifyTimeout(Waitlist w) {
        try {
            Course c = courseRepo.findById(w.getCourseId()).orElse(null);
            notificationService.notify(w.getStudentId(), "waitlist_timeout", "候补超时通知",
                    "《" + (c != null ? c.getName() : "课程") + "》的递补名额因未及时确认已释放给下一位",
                    w.getId());
        } catch (Exception ignore) {}
    }

    /** 预计递补等待时长（毫秒）：按该课程历史递补间隔估算；数据不足返回 null */
    public Long estimatedWait(Long courseId, int position) {
        List<Waitlist> hist = waitlistRepo.findByCourseIdAndStatusIn(courseId, List.of("confirming", "confirmed", "timeout"));
        List<LocalDateTime> ts = new ArrayList<>();
        int mins = confirmMinutes();
        for (Waitlist w : hist) {
            LocalDateTime t = w.getPromoteTime();
            if (t == null && w.getDeadline() != null) t = w.getDeadline().minusMinutes(mins);
            if (t != null) ts.add(t);
        }
        if (ts.size() < 2) return null;
        ts.sort(Comparator.naturalOrder());
        long span = java.time.Duration.between(ts.get(0), ts.get(ts.size() - 1)).toMillis();
        long gap = span / (ts.size() - 1);
        if (gap <= 0) return null;
        long est = gap * Math.max(position, 1);
        return Math.max(est, 30 * 60 * 1000L);
    }

    // 定时扫描超时未确认的名额并释放给下一位
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void timeoutScan() {
        List<Waitlist> overdue = waitlistRepo.findByStatusAndDeadlineBefore("confirming", LocalDateTime.now());
        if (overdue.isEmpty()) return;
        Set<String> keys = new HashSet<>();
        for (Waitlist w : overdue) {
            w.setStatus("timeout");
            waitlistRepo.save(w);
            notifyTimeout(w);
            keys.add(w.getCourseId() + ":" + w.getRound());
        }
        for (String k : keys) {
            String[] p = k.split(":");
            promoteIfNeeded(Long.valueOf(p[0]), Integer.parseInt(p[1]));
        }
    }

    // ===== 时间冲突检测（与已录取课程） =====
    public boolean hasTimeConflict(Long studentId, Course course, int round) {
        List<Enrollment> mine = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus())).toList();
        for (Enrollment e : mine) {
            Course ec = courseRepo.findById(e.getCourseId()).orElse(null);
            if (ec != null && conflict(course.getTime(), ec.getTime())) return true;
        }
        return false;
    }

    private boolean conflict(String t1, String t2) {
        for (Map<String, String> a : parse(t1)) {
            for (Map<String, String> b : parse(t2)) {
                if (a.get("day").equals(b.get("day"))) {
                    int aS = toMin(a.get("start")), aE = toMin(a.get("end"));
                    int bS = toMin(b.get("start")), bE = toMin(b.get("end"));
                    if (aS < bE && bS < aE) return true;
                }
            }
        }
        return false;
    }

    private List<Map<String, String>> parse(String timeStr) {
        List<Map<String, String>> slots = new ArrayList<>();
        if (timeStr == null) return slots;
        Pattern p = Pattern.compile("(周[一二三四五六日])\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})");
        for (String part : timeStr.split("/")) {
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

    private Map<String, Object> ok(String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("success", true); r.put("msg", msg);
        return r;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("success", false); r.put("msg", msg);
        return r;
    }
}
