package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.CourseReview;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.CourseReviewRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.*;

/** 课程评价与评分业务：多维度（教学/内容/作业）+ 匿名开关 */
@Service
public class ReviewService {

    @Autowired private CourseReviewRepository reviewRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private CourseService courseService;
    @Autowired private UserRepository userRepo;

    /** 学生是否已录取过该课程（有资格评价） */
    private boolean hasStudied(Long studentId, Long courseId) {
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (e.getCourseId().equals(courseId) && "approved".equals(e.getStatus())) return true;
        }
        return false;
    }

    /** 姓名脱敏：张三 -> 张同学 */
    private String maskName(Long studentId) {
        User u = userRepo.findById(studentId).orElse(null);
        if (u == null || u.getName() == null || u.getName().isEmpty()) return "匿名同学";
        return u.getName().substring(0, 1) + "同学";
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    /** 维度评分（旧数据回退到综合评分） */
    private int tOf(CourseReview r) { return r.getTeachingRating() != null ? r.getTeachingRating() : nz(r.getRating()); }
    private int cOf(CourseReview r) { return r.getContentRating() != null ? r.getContentRating() : nz(r.getRating()); }
    private int hOf(CourseReview r) { return r.getHomeworkRating() != null ? r.getHomeworkRating() : nz(r.getRating()); }

    private double round1(double v) { return Math.round(v * 10) / 10.0; }

    private Map<String, Object> row(CourseReview r, boolean withCourse) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reviewId", r.getId());
        m.put("courseId", r.getCourseId());
        m.put("rating", r.getRating());
        m.put("teaching", tOf(r));
        m.put("contentScore", cOf(r));
        m.put("homework", hOf(r));
        boolean anon = Boolean.TRUE.equals(r.getAnonymous());
        m.put("anonymous", anon);
        m.put("content", r.getContent());
        m.put("studentName", anon ? "匿名同学" : maskName(r.getStudentId()));
        m.put("mine", false);
        m.put("createdAt", r.getCreatedAt() != null ? java.sql.Timestamp.valueOf(r.getCreatedAt()).getTime() : null);
        if (withCourse) {
            Course c = courseRepo.findById(r.getCourseId()).orElse(null);
            m.put("courseName", c != null ? c.getName() : "（课程已删除）");
            m.put("teacherName", c != null ? courseService.teacherName(c.getTeacherId()) : "");
        }
        return m;
    }

    @Transactional
    public Map<String, Object> submit(Long studentId, Long courseId, Integer rating, String content,
                                      Integer teaching, Integer contentScore, Integer homework, Boolean anonymous) {
        Map<String, Object> r = new HashMap<>();
        Course c = courseRepo.findById(courseId).orElse(null);
        if (c == null) { r.put("success", false); r.put("msg", "课程不存在"); return r; }
        if (rating == null || rating < 1 || rating > 5) { r.put("success", false); r.put("msg", "请选择 1-5 星综合评分"); return r; }
        for (Integer v : new Integer[]{teaching, contentScore, homework}) {
            if (v != null && (v < 1 || v > 5)) { r.put("success", false); r.put("msg", "维度评分需为 1-5 星"); return r; }
        }
        if (!hasStudied(studentId, courseId)) { r.put("success", false); r.put("msg", "只有录取过该课程的学生才能评价"); return r; }

        CourseReview rev = reviewRepo.findByStudentIdAndCourseId(studentId, courseId).orElse(null);
        boolean isNew = rev == null;
        if (isNew) {
            rev = new CourseReview();
            rev.setStudentId(studentId);
            rev.setCourseId(courseId);
            rev.setCreatedAt(LocalDateTime.now());
        }
        rev.setRating(rating);
        rev.setTeachingRating(teaching != null ? teaching : rating);
        rev.setContentRating(contentScore != null ? contentScore : rating);
        rev.setHomeworkRating(homework != null ? homework : rating);
        rev.setAnonymous(Boolean.TRUE.equals(anonymous));
        rev.setContent(content);
        rev.setUpdatedAt(LocalDateTime.now());
        reviewRepo.save(rev);
        r.put("success", true);
        r.put("msg", isNew ? "评价已提交，感谢反馈" : "评价已更新");
        return r;
    }

    /** 某课程的评价明细 + 统计（含三维度均分） */
    public Map<String, Object> courseReviews(Long courseId, Long viewerId) {
        Course c = courseRepo.findById(courseId).orElse(null);
        List<CourseReview> list = reviewRepo.findByCourseIdOrderByCreatedAtDesc(courseId);
        int[] dist = new int[5];
        int sum = 0, tSum = 0, cSum = 0, hSum = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CourseReview rev : list) {
            sum += nz(rev.getRating());
            tSum += tOf(rev); cSum += cOf(rev); hSum += hOf(rev);
            if (nz(rev.getRating()) >= 1 && nz(rev.getRating()) <= 5) dist[rev.getRating() - 1]++;
            Map<String, Object> m = row(rev, false);
            if (viewerId != null && viewerId.equals(rev.getStudentId())) m.put("mine", true);
            rows.add(m);
        }
        int n = list.size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("courseId", courseId);
        out.put("courseName", c != null ? c.getName() : "");
        out.put("teacherName", c != null ? courseService.teacherName(c.getTeacherId()) : "");
        out.put("count", n);
        out.put("avg", n == 0 ? 0.0 : round1((double) sum / n));
        out.put("teachingAvg", n == 0 ? 0.0 : round1((double) tSum / n));
        out.put("contentAvg", n == 0 ? 0.0 : round1((double) cSum / n));
        out.put("homeworkAvg", n == 0 ? 0.0 : round1((double) hSum / n));
        out.put("dist", dist);
        out.put("list", rows);
        return out;
    }

    /** 全部课程评分汇总：{ "1": {avg, count}, ... } */
    public Map<String, Object> allRatings() {
        Map<Long, int[]> agg = new HashMap<>();
        for (CourseReview rev : reviewRepo.findAll()) {
            int[] a = agg.computeIfAbsent(rev.getCourseId(), k -> new int[2]);
            a[0] += nz(rev.getRating());
            a[1]++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Long, int[]> e : agg.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("avg", round1((double) e.getValue()[0] / e.getValue()[1]));
            m.put("count", e.getValue()[1]);
            out.put(String.valueOf(e.getKey()), m);
        }
        return out;
    }

    public List<Map<String, Object>> mine(Long studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CourseReview rev : reviewRepo.findByStudentIdOrderByCreatedAtDesc(studentId)) {
            Map<String, Object> m = row(rev, true);
            m.putAll(semOf(studentId, rev.getCourseId()));
            out.add(m);
        }
        return out;
    }

    /** 课程在学生修读记录中的学期序与学年学期名（评教按学期展示用） */
    private Map<String, Object> semOf(Long studentId, Long courseId) {
        Map<String, Object> m = new LinkedHashMap<>();
        Integer sem = enrollmentRepo.findByStudentId(studentId).stream()
                .filter(e -> courseId.equals(e.getCourseId()) && "approved".equals(e.getStatus()) && e.getSemesterNo() != null)
                .map(Enrollment::getSemesterNo)
                .findFirst().orElse(null);
        m.put("semesterNo", sem);
        if (sem != null) {
            Integer grade = userRepo.findById(studentId).map(User::getGrade).orElse(null);
            m.put("semesterLabel", com.zhixue.service.GraduationService.semesterLabel(grade, sem));
        } else {
            m.put("semesterLabel", null);
        }
        return m;
    }

    /** 已录取但还没评价的课程 */
    public List<Map<String, Object>> pending(Long studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Long> reviewed = new HashSet<>();
        for (CourseReview rev : reviewRepo.findByStudentIdOrderByCreatedAtDesc(studentId)) reviewed.add(rev.getCourseId());
        Set<Long> seen = new HashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            if (reviewed.contains(e.getCourseId()) || seen.contains(e.getCourseId())) continue;
            seen.add(e.getCourseId());
            Course c = courseRepo.findById(e.getCourseId()).orElse(null);
            if (c == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("category", c.getCategory());
            m.put("credit", c.getCredit());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.putAll(semOf(studentId, c.getId()));
            out.add(m);
        }
        return out;
    }

    /** 教师视角：我教的课程各自评分（含三维度均分） */
    public Map<String, Object> teacherSummary(Long teacherId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int totalCount = 0, totalSum = 0;
        int[] totalDist = new int[6];
        for (Course c : courseService.findByTeacher(teacherId)) {
            List<CourseReview> list = reviewRepo.findByCourseIdOrderByCreatedAtDesc(c.getId());
            int sum = 0, tSum = 0, cSum = 0, hSum = 0;
            for (CourseReview rev : list) {
                sum += nz(rev.getRating());
                tSum += tOf(rev); cSum += cOf(rev); hSum += hOf(rev);
                int r = Math.max(1, Math.min(5, nz(rev.getRating())));
                totalDist[r]++;
            }
            int n = list.size();
            totalCount += n;
            totalSum += sum;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("count", n);
            m.put("avg", n == 0 ? 0.0 : round1((double) sum / n));
            m.put("teachingAvg", n == 0 ? 0.0 : round1((double) tSum / n));
            m.put("contentAvg", n == 0 ? 0.0 : round1((double) cSum / n));
            m.put("homeworkAvg", n == 0 ? 0.0 : round1((double) hSum / n));
            m.put("latest", list.isEmpty() ? null : row(list.get(0), false));
            rows.add(m);
        }
        // 全校维度均分（含其他教师的课），用于雷达对比
        List<CourseReview> all = reviewRepo.findAll();
        double sT = 0, sC = 0, sH = 0;
        for (CourseReview rev : all) { sT += tOf(rev); sC += cOf(rev); sH += hOf(rev); }
        int sN = all.size();
        Map<String, Object> schoolAvg = new LinkedHashMap<>();
        schoolAvg.put("teaching", sN == 0 ? 0.0 : round1(sT / sN));
        schoolAvg.put("content", sN == 0 ? 0.0 : round1(sC / sN));
        schoolAvg.put("homework", sN == 0 ? 0.0 : round1(sH / sN));

        List<Map<String, Object>> dist = new ArrayList<>();
        for (int s = 5; s >= 1; s--) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("star", s);
            d.put("count", totalDist[s]);
            d.put("rate", totalCount == 0 ? 0.0 : Math.round(totalDist[s] * 1000.0 / totalCount) / 10.0);
            dist.add(d);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("courses", rows);
        out.put("totalCount", totalCount);
        out.put("totalAvg", totalCount == 0 ? 0.0 : round1((double) totalSum / totalCount));
        out.put("dist", dist);
        out.put("goodRate", totalCount == 0 ? 0.0
                : Math.round((totalDist[4] + totalDist[5]) * 1000.0 / totalCount) / 10.0);
        out.put("myTeaching", totalCount == 0 ? 0.0
                : round1(rows.stream().mapToDouble(c -> (Double) c.get("teachingAvg") * (int) c.get("count")).sum() / totalCount));
        out.put("myContent", totalCount == 0 ? 0.0
                : round1(rows.stream().mapToDouble(c -> (Double) c.get("contentAvg") * (int) c.get("count")).sum() / totalCount));
        out.put("myHomework", totalCount == 0 ? 0.0
                : round1(rows.stream().mapToDouble(c -> (Double) c.get("homeworkAvg") * (int) c.get("count")).sum() / totalCount));
        out.put("schoolAvg", schoolAvg);
        return out;
    }
}
