package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.User;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.GradeRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/** 教师端「学生画像」：单个学生的课程、成绩、学分与选课行为聚合 */
@Service
public class StudentProfileService {

    @Autowired private UserRepository userRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private GradeRepository gradeRepo;
    @Autowired private CourseService courseService;
    @Autowired private GraduationService graduationService;
    @Autowired private GradeService gradeService;
    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private EnrollmentService enrollmentService;

    private int nz(Integer v) { return v == null ? 0 : v; }
    private double r1(double v) { return Math.round(v * 10) / 10.0; }

    public Map<String, Object> profile(Long studentId) {
        User stu = userRepo.findById(studentId).orElse(null);
        Map<String, Object> student = new LinkedHashMap<>();
        student.put("id", studentId);
        student.put("name", stu != null ? stu.getName() : "未知学生");
        student.put("no", stu != null ? stu.getNo() : "");
        student.put("major", stu != null ? stu.getMajor() : "");
        student.put("className", stu != null ? stu.getClassNo() : "");
        student.put("grade", stu != null ? stu.getGrade() : null);
        student.put("college", stu != null ? stu.getCollege() : "");

        int round = enrollmentService.getSystemConfig().getCurrentRound();

        // 课程明细：选课记录 × 课程信息 × 成绩
        Map<Long, Grade> gradeMap = new HashMap<>();
        for (Grade g : gradeRepo.findByStudentId(studentId)) gradeMap.put(g.getCourseId(), g);

        List<Enrollment> enrolls = enrollmentRepo.findByStudentId(studentId);
        enrolls.sort(Comparator.comparing(Enrollment::getRound, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Map<String, Object>> courses = new ArrayList<>();
        int totalCredits = 0, semCredits = 0, dropped = 0;
        List<Double> scored = new ArrayList<>();
        Set<Long> waitCourses = new HashSet<>();

        for (Enrollment e : enrolls) {
            Course c = courseService.findById(e.getCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("courseId", e.getCourseId());
            m.put("courseName", c != null ? c.getName() : "已删除课程");
            m.put("teacherName", c != null ? courseService.teacherName(c.getTeacherId()) : "");
            m.put("category", c != null ? c.getCategory() : "");
            m.put("credit", c != null ? nz(c.getCredit()) : 0);
            m.put("round", e.getRound());
            m.put("points", e.getPoints());
            m.put("status", e.getStatus());
            Grade g = gradeMap.get(e.getCourseId());
            if (g != null) {
                m.put("totalScore", g.getTotalScore());
                m.put("regularScore", g.getRegularScore());
                m.put("finalScore", g.getFinalScore());
            }
            courses.add(m);

            if ("dropped".equals(e.getStatus())) { dropped++; continue; }
            if ("approved".equals(e.getStatus())) {
                int credit = c != null ? nz(c.getCredit()) : 0;
                totalCredits += credit;
                if (e.getRound() != null && e.getRound() == round) semCredits += credit;
            }
            if ("rejected".equals(e.getStatus())) continue;
            // 候补过的课程（不同轮次去重）
            if (waitlistRepo.findByStudentIdAndCourseIdAndRound(e.getStudentId(), e.getCourseId(),
                    e.getRound() == null ? round : e.getRound()).isPresent()) {
                waitCourses.add(e.getCourseId());
            }
            if (g != null && g.getTotalScore() != null) scored.add(g.getTotalScore());
        }

        int waitlistCount = waitCourses.size();
        // GPA 与个人成绩页、毕业判定共用后端成绩流水有效记录口径
        Map<String, Object> gpaInfo = gradeService.gpaOfStudent(studentId);

        // 成绩分布（10 分一段）
        int[] buckets = new int[10];
        for (Double s : scored) buckets[Math.max(0, Math.min(9, (int) Math.floor(s / 10)))]++;
        List<Map<String, Object>> gradeDist = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("low", i * 10);
            b.put("count", buckets[i]);
            gradeDist.add(b);
        }

        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("courseCount", (int) courses.stream().filter(c -> !"dropped".equals(c.get("status"))).count());
        stat.put("totalCredits", totalCredits);
        stat.put("semesterCredits", semCredits);
        stat.put("avgScore", scored.isEmpty() ? null : r1(scored.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        stat.put("maxScore", scored.isEmpty() ? null : Collections.max(scored));
        stat.put("minScore", scored.isEmpty() ? null : Collections.min(scored));
        Object gpaCount = gpaInfo.get("courseCount");
        stat.put("gpa", gpaCount instanceof Number && ((Number) gpaCount).intValue() > 0 ? gpaInfo.get("gpa") : null);
        stat.put("dropped", dropped);
        stat.put("waitlist", waitlistCount);

        // 毕业进度（复用毕业服务，剔除推荐课程列表减小载荷）
        Map<String, Object> grad = new LinkedHashMap<>((Map<String, Object>) graduationService.summary(studentId));
        grad.remove("recommend");

        // 行为预警（无考勤数据，用退课/候补行为推断）
        List<Map<String, Object>> warnings = new ArrayList<>();
        if (dropped >= 2) warnings.add(Map.of("level", "warn", "text", "退课 " + dropped + " 次，关注选课稳定性"));
        if (waitlistCount >= 2) warnings.add(Map.of("level", "info", "text", "进入候补 " + waitlistCount + " 门，选课计划偏激进"));
        if (semCredits > 0 && semCredits >= 24) warnings.add(Map.of("level", "info", "text", "本学期 " + semCredits + " 学分，课业负担较重"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("student", student);
        out.put("courses", courses);
        out.put("stats", stat);
        out.put("gradeDist", gradeDist);
        out.put("graduation", grad);
        out.put("warnings", warnings);
        return out;
    }
}
