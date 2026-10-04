package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.CourseReview;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.CourseReviewRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/** 管理端统计报表：课程热度 / 轮次趋势 / 满员率 / 教师工作量 */
@Service
public class StatsService {

    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private CourseReviewRepository reviewRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private CourseService courseService;

    private int nz(Integer v) { return v == null ? 0 : v; }
    private double pct(int a, int b) { return b <= 0 ? 0.0 : Math.round(a * 1000.0 / b) / 10.0; }

    public Map<String, Object> report() {
        int round = sysRepo.findById(1L).map(c -> c.getCurrentRound() == null ? 1 : c.getCurrentRound()).orElse(1);
        List<Course> courses = courseRepo.findAll();
        List<Enrollment> all = enrollmentRepo.findAll();

        // 课程热度 + 满员率（本轮，未退课）
        List<Map<String, Object>> hot = new ArrayList<>();
        List<Map<String, Object>> fill = new ArrayList<>();
        int capSum = 0, approvedSum = 0, appliedSum = 0, fullCount = 0, waitSum = 0;
        for (Course c : courses) {
            boolean req = c.getCategory() != null && c.getCategory().contains("必修");
            List<Enrollment> ce = all.stream()
                    .filter(e -> c.getId().equals(e.getCourseId()) && e.getRound() != null && e.getRound() == round
                            && !"dropped".equals(e.getStatus()))
                    .toList();
            int applied = ce.size();
            int approved = (int) ce.stream().filter(e -> "approved".equals(e.getStatus())).count();
            int cap = nz(c.getCapacity());
            int wait = waitlistRepo.findByCourseIdAndRoundAndStatusInOrderByJoinTimeAsc(c.getId(), round, WaitlistService.ACTIVE).size();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("capacity", cap);
            m.put("applied", applied);
            m.put("approved", approved);
            m.put("waiting", wait);
            m.put("fillRate", pct(approved, cap));
            m.put("required", req);
            hot.add(m);
            fill.add(m);
            if (!req) {
                capSum += cap; approvedSum += approved; appliedSum += applied; waitSum += wait;
                if (cap > 0 && approved >= cap) fullCount++;
            }
        }
        hot.sort((a, b) -> Integer.compare((int) b.get("applied"), (int) a.get("applied")));
        fill.sort((a, b) -> Double.compare((double) b.get("fillRate"), (double) a.get("fillRate")));

        // 轮次报名趋势
        int maxRound = all.stream().filter(e -> e.getRound() != null).mapToInt(Enrollment::getRound).max().orElse(round);
        maxRound = Math.max(maxRound, round);
        List<Map<String, Object>> rounds = new ArrayList<>();
        for (int r = 1; r <= maxRound; r++) {
            final int rr = r;
            List<Enrollment> re = all.stream().filter(e -> e.getRound() != null && e.getRound() == rr).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("round", r);
            m.put("applied", (int) re.stream().filter(e -> !"dropped".equals(e.getStatus())).count());
            m.put("approved", (int) re.stream().filter(e -> "approved".equals(e.getStatus())).count());
            m.put("rejected", (int) re.stream().filter(e -> "rejected".equals(e.getStatus())).count());
            m.put("dropped", (int) re.stream().filter(e -> "dropped".equals(e.getStatus())).count());
            rounds.add(m);
        }

        // 教师工作量
        List<Map<String, Object>> teachers = new ArrayList<>();
        for (User u : userRepo.findAll()) {
            if (!"teacher".equals(u.getRole())) continue;
            List<Course> tc = courseService.findByTeacher(u.getId());
            Set<Long> tcIds = new HashSet<>();
            for (Course c : tc) tcIds.add(c.getId());
            int students = (int) all.stream()
                    .filter(e -> e.getRound() != null && e.getRound() == round && "approved".equals(e.getStatus())
                            && tcIds.contains(e.getCourseId()))
                    .count();
            int rc = 0, rs = 0;
            for (Course c : tc) {
                for (CourseReview rev : reviewRepo.findByCourseIdOrderByCreatedAtDesc(c.getId())) {
                    rs += nz(rev.getRating());
                    rc++;
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teacherId", u.getId());
            m.put("teacherName", u.getName());
            m.put("college", u.getCollege());
            m.put("courseCount", tc.size());
            m.put("studentCount", students);
            m.put("reviewCount", rc);
            m.put("avgRating", rc == 0 ? 0.0 : Math.round((double) rs / rc * 10) / 10.0);
            teachers.add(m);
        }
        teachers.sort((a, b) -> Integer.compare((int) b.get("studentCount"), (int) a.get("studentCount")));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("round", round);
        overview.put("totalCourses", courses.size());
        overview.put("totalUsers", userRepo.count());
        overview.put("applied", appliedSum);
        overview.put("approved", approvedSum);
        overview.put("fillRate", pct(approvedSum, capSum));
        overview.put("fullCount", fullCount);
        overview.put("waiting", waitSum);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overview", overview);
        out.put("hot", hot);
        out.put("fill", fill);
        out.put("rounds", rounds);
        out.put("teachers", teachers);
        return out;
    }
}
