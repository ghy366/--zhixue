package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.EnrollmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraduationService {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseService courseService;
    @Autowired private EnrollmentRepository enrollmentRepo;

    private static final String[] KEYS = {"必修", "选修", "通识", "实践"};

    /** 课程类别 → 毕业学分大类（必修/选修/通识/实践） */
    public String groupOf(String category) {
        if (category == null) return "选修";
        if (category.contains("必修")) return "必修";
        if (category.contains("通识")) return "通识";
        if (category.contains("实践")) return "实践";
        return "选修";
    }

    /** 毕业进度汇总：各类别已获/差额 + 推荐可修课程 */
    public Map<String, Object> summary(Long studentId) {
        SystemConfig cfg = enrollmentService.getSystemConfig();
        Map<String, Integer> reqMap = new LinkedHashMap<>();
        reqMap.put("必修", nz(cfg.getGradReqRequired()));
        reqMap.put("选修", nz(cfg.getGradReqElective()));
        reqMap.put("通识", nz(cfg.getGradReqGeneral()));
        reqMap.put("实践", nz(cfg.getGradReqPractice()));

        Map<String, Integer> earned = new LinkedHashMap<>();
        for (String k : KEYS) earned.put(k, 0);
        Set<Long> approvedIds = new HashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;
            approvedIds.add(c.getId());
            String g = groupOf(c.getCategory());
            earned.put(g, earned.get(g) + nz(c.getCredit()));
        }

        List<Map<String, Object>> categories = new ArrayList<>();
        int totalReq = 0, totalEarned = 0;
        for (String k : KEYS) {
            int req = reqMap.get(k), got = earned.get(k);
            totalReq += req; totalEarned += got;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", k);
            m.put("required", req);
            m.put("earned", got);
            m.put("gap", Math.max(0, req - got));
            m.put("percent", req <= 0 ? 0 : (int) Math.round(got * 100.0 / req));
            m.put("done", got >= req);
            categories.add(m);
        }

        List<Map<String, Object>> recommend = recommendCourses(approvedIds, earned, reqMap);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("required", totalReq);
        total.put("earned", totalEarned);
        total.put("gap", Math.max(0, totalReq - totalEarned));
        total.put("percent", totalReq <= 0 ? 0 : (int) Math.round(totalEarned * 100.0 / totalReq));
        result.put("total", total);
        result.put("recommend", recommend);
        return result;
    }

    /** 推荐可修课程：只推荐仍有差额的模块中的、学生尚未录取的课程 */
    private List<Map<String, Object>> recommendCourses(Set<Long> approvedIds,
                                                       Map<String, Integer> earned,
                                                       Map<String, Integer> reqMap) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Course c : courseService.findAll()) {
            if (approvedIds.contains(c.getId())) continue;
            String g = groupOf(c.getCategory());
            int gap = reqMap.getOrDefault(g, 0) - earned.getOrDefault(g, 0);
            if (gap <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("category", c.getCategory());
            m.put("credit", nz(c.getCredit()));
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("location", c.getLocation());
            m.put("intro", c.getIntro());
            m.put("group", g);
            m.put("groupGap", gap);
            out.add(m);
        }
        out.sort((a, b) -> {
            int d = Integer.compare((Integer) b.get("groupGap"), (Integer) a.get("groupGap"));
            if (d != 0) return d;
            return Integer.compare((Integer) b.get("credit"), (Integer) a.get("credit"));
        });
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }
}
