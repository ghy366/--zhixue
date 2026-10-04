package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.repository.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScheduleService {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseService courseService;
    @Autowired private EnrollmentRepository enrollmentRepo;

    private static final Pattern TIME_P = Pattern.compile("(周[一二三四五六日])\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})");

    private List<Map<String, String>> parseSlots(String timeStr) {
        List<Map<String, String>> slots = new ArrayList<>();
        if (timeStr == null) return slots;
        for (String part : timeStr.split("/")) {
            Matcher m = TIME_P.matcher(part.trim());
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

    private boolean slotsOverlap(List<Map<String, String>> a, List<Map<String, String>> b) {
        for (Map<String, String> sa : a) {
            for (Map<String, String> sb : b) {
                if (!sa.get("day").equals(sb.get("day"))) continue;
                int aS = toMin(sa.get("start")), aE = toMin(sa.get("end"));
                int bS = toMin(sb.get("start")), bE = toMin(sb.get("end"));
                if (aS < bE && bS < aE) return true;
            }
        }
        return false;
    }

    /** 校区冲突：同日相邻时段（间隔 < 30 分钟）且校区不同 */
    private boolean campusAdjacent(Course a, Course b) {
        String ca = a.getCampus(), cb = b.getCampus();
        if (ca == null || cb == null || ca.isBlank() || cb.isBlank() || ca.equals(cb)) return false;
        for (Map<String, String> sa : parseSlots(a.getTime())) {
            for (Map<String, String> sb : parseSlots(b.getTime())) {
                if (!sa.get("day").equals(sb.get("day"))) continue;
                int aS = toMin(sa.get("start")), aE = toMin(sa.get("end"));
                int bS = toMin(sb.get("start")), bE = toMin(sb.get("end"));
                if (aE <= bS && (bS - aE) < 30) return true;
                if (bE <= aS && (aS - bE) < 30) return true;
            }
        }
        return false;
    }

    private String examOf(Course c) {
        return (c.getExamTime() != null && !c.getExamTime().isBlank()) ? c.getExamTime() : c.getTime();
    }

    private List<Course> approvedCourses(Long studentId, int round) {
        List<Course> out = new ArrayList<>();
        for (Enrollment e : enrollmentRepo.findByStudentIdAndRound(studentId, round)) {
            if (!"approved".equals(e.getStatus())) continue;
            Course c = courseService.findById(e.getCourseId());
            if (c != null) out.add(c);
        }
        return out;
    }

    /** 检测当前已录取课程的时间/校区/考试冲突 */
    public Map<String, Object> conflicts(Long studentId) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Course> list = approvedCourses(studentId, round);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Course a = list.get(i), b = list.get(j);
                if (slotsOverlap(parseSlots(a.getTime()), parseSlots(b.getTime()))) {
                    out.add(conf("time", a, b, "「" + a.getName() + "」与「" + b.getName() + "」上课时间重叠"));
                } else {
                    if (slotsOverlap(parseSlots(examOf(a)), parseSlots(examOf(b)))) {
                        out.add(conf("exam", a, b, "「" + a.getName() + "」与「" + b.getName() + "」考试时间重叠"));
                    }
                    if (campusAdjacent(a, b)) {
                        out.add(conf("campus", a, b, "「" + a.getName() + "」与「" + b.getName() + "」相邻时段但跨校区（" + a.getCampus() + " ↔ " + b.getCampus() + "）"));
                    }
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conflicts", out);
        result.put("count", out.size());
        return result;
    }

    private Map<String, Object> conf(String type, Course a, Course b, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("courseAId", a.getId()); m.put("courseAName", a.getName());
        m.put("courseBId", b.getId()); m.put("courseBName", b.getName());
        m.put("detail", detail);
        return m;
    }

    /** 智能排课：锁定课程优先，其余候选按学分降序贪心加入，避开时间冲突 */
    public Map<String, Object> generate(Long studentId, List<Long> candidates, List<Long> locks) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Course> chosen = new ArrayList<>(approvedCourses(studentId, round));
        Set<Long> chosenIds = new HashSet<>();
        for (Course c : chosen) chosenIds.add(c.getId());

        Map<Long, Course> candMap = new LinkedHashMap<>();
        for (Long id : (candidates == null ? new ArrayList<Long>() : candidates)) {
            Course c = courseService.findById(id);
            if (c != null) candMap.put(id, c);
        }

        List<Long> added = new ArrayList<>(), rejected = new ArrayList<>();
        Set<Long> lockedIds = new HashSet<>(locks == null ? List.of() : locks);
        // 1. 锁定课程先加入
        for (Long id : lockedIds) {
            Course c = candMap.get(id);
            if (c == null || chosenIds.contains(c.getId())) continue;
            if (hasConflictWith(c, chosen)) { rejected.add(c.getId()); continue; }
            chosen.add(c); chosenIds.add(c.getId()); added.add(c.getId());
        }
        // 2. 其余候选按学分降序贪心
        List<Course> rest = new ArrayList<>();
        for (Course c : candMap.values()) {
            if (!chosenIds.contains(c.getId()) && !lockedIds.contains(c.getId())) rest.add(c);
        }
        rest.sort((x, y) -> {
            int d = Integer.compare(nz(y.getCredit()), nz(x.getCredit()));
            return d != 0 ? d : Long.compare(x.getId(), y.getId());
        });
        for (Course c : rest) {
            if (hasConflictWith(c, chosen)) { rejected.add(c.getId()); continue; }
            chosen.add(c); chosenIds.add(c.getId()); added.add(c.getId());
        }

        List<Map<String, Object>> schedule = new ArrayList<>();
        for (Course c : chosen) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("category", c.getCategory());
            m.put("credit", nz(c.getCredit()));
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("location", c.getLocation());
            m.put("campus", c.getCampus());
            schedule.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.put("rejected", rejected);
        result.put("schedule", schedule);
        return result;
    }

    private boolean hasConflictWith(Course c, List<Course> chosen) {
        for (Course x : chosen) {
            if (slotsOverlap(parseSlots(c.getTime()), parseSlots(x.getTime()))) return true;
        }
        return false;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }
}
