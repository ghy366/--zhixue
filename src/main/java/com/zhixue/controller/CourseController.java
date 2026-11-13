package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Course;
import com.zhixue.entity.User;
import com.zhixue.service.CourseService;
import com.zhixue.service.EnrollmentService;
import com.zhixue.service.IntentService;
import com.zhixue.service.ReviewService;
import com.zhixue.service.WaitlistService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired private CourseService courseService;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private WaitlistService waitlistService;
    @Autowired private IntentService intentService;
    @Autowired private ReviewService reviewService;
    @Autowired private com.zhixue.repository.UserRepository userRepository;

    /**
     * 课程列表。支持服务端分页：传 page/size 时返回 {items,total,page,size,categories}，
     * 未传时保持旧的全量数组（旧调用方零破坏）。electiveOnly 供学生课程浏览剔除必修；
     * sort：competition 竞争比（guide.bidCount 退化为报名数 ÷ 容量）/ remain 剩余名额 / credit 学分。
     */
    @GetMapping
    public ApiResponse<Object> list(
            @RequestParam(required = false, defaultValue = "全部") String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean electiveOnly,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = token != null ? SessionStore.getUserId(token.replace("Bearer ", "")) : null;
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Course> courses = courseService.search(keyword, category);
        if (Boolean.TRUE.equals(electiveOnly)) {
            // 必修课由教学计划自动安排，不参与选课浏览
            courses = courses.stream()
                    .filter(c -> c.getCategory() == null || !c.getCategory().contains("必修"))
                    .toList();
        }
        Map<String, Object> ratings = reviewService.allRatings();
        // 学生视角按「本学期」统计报名/余量：历史学期的报名记录保留但不挤占新学期名额
        Integer semesterNo = enrollmentService.currentSemesterOf(userId);
        List<Map<String, Object>> all = new ArrayList<>();
        for (Course c : courses) {
            all.add(courseToMap(c, userId, round, ratings, semesterNo));
        }
        // 排序口径与前端本地排序一致（迁移到服务端后跨页排序才正确）
        if ("competition".equals(sort)) {
            all.sort((a, b) -> Double.compare(heatRatioOf(b), heatRatioOf(a)));
        } else if ("remain".equals(sort)) {
            all.sort((a, b) -> Integer.compare(remainOf(b), remainOf(a)));
        } else if ("credit".equals(sort)) {
            all.sort((a, b) -> Integer.compare(numInt(b.get("credit")), numInt(a.get("credit"))));
        }
        // 全量类别列表：分页模式下分类下拉仍能看到所有可选类别
        Set<String> cats = new LinkedHashSet<>();
        for (Course c : courses) {
            if (c.getCategory() != null && !c.getCategory().contains("必修")) cats.add(c.getCategory());
        }
        if (page == null || size == null || page < 1 || size < 1) {
            return ApiResponse.ok(all);   // 兼容模式：旧调用方拿全量数组
        }
        int from = Math.min(all.size(), (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", all.subList(from, to));
        data.put("total", all.size());
        data.put("page", page);
        data.put("size", size);
        data.put("categories", cats);
        return ApiResponse.ok(data);
    }

    private double heatRatioOf(Map<String, Object> m) {
        int cap = numInt(m.get("capacity"));
        if (cap <= 0) return 0;
        Object bid = m.get("guide") instanceof Map<?, ?> g ? g.get("bidCount") : null;
        Object total = m.get("stat") instanceof Map<?, ?> s ? s.get("total") : null;
        return (bid != null ? ((Number) bid).doubleValue()
                : total != null ? ((Number) total).doubleValue() : 0) / cap;
    }

    private int remainOf(Map<String, Object> m) {
        int cap = numInt(m.get("capacity"));
        Object total = m.get("stat") instanceof Map<?, ?> s ? s.get("total") : null;
        return Math.max(0, cap - (total == null ? 0 : ((Number) total).intValue()));
    }

    private int numInt(Object v) { return v instanceof Number n ? n.intValue() : 0; }

    @GetMapping("/teacher/{teacherId}")
    public ApiResponse<List<Map<String, Object>>> teacherCourses(@PathVariable Long teacherId) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Course> courses = courseService.findByTeacher(teacherId);
        Map<String, Object> ratings = reviewService.allRatings();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Course c : courses) result.add(courseToMap(c, null, round, ratings));
        return ApiResponse.ok(result);
    }

    /** 「选了这门课的人也选了」：基于共选记录的协同过滤推荐 */
    @GetMapping("/{id}/also-took")
    public ApiResponse<List<Map<String, Object>>> alsoTook(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "6") int n) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : enrollmentService.alsoTookCounts(id, n)) {
            Object tid = t.get("teacherId");
            t.put("teacherName", tid == null ? "未分配" : courseService.teacherName(Long.valueOf(tid.toString())));
            out.add(t);
        }
        return ApiResponse.ok(out);
    }

    @PostMapping
    public ApiResponse<Course> add(@RequestBody Course c) {
        String err = teacherLoadError(c.getTeacherId(), null);
        if (err != null) return ApiResponse.fail(err);
        return ApiResponse.ok("添加成功", courseService.save(c));
    }

    @PutMapping("/{id}")
    public ApiResponse<Course> update(@PathVariable Long id, @RequestBody Course c) {
        if (courseService.findById(id) == null) return ApiResponse.fail("课程不存在");
        String err = teacherLoadError(c.getTeacherId(), id);
        if (err != null) return ApiResponse.fail(err);
        c.setId(id);
        return ApiResponse.ok("更新成功", courseService.save(c));
    }

    /**
     * 教师课时上限校验：同一教师本学期授课门数达到上限（默认 3 门）后，
     * 不允许再新增/转入课程（真实高校教师工作量约束）。
     */
    private String teacherLoadError(Long teacherId, Long excludeCourseId) {
        if (teacherId == null) return null;
        int max = enrollmentService.getSystemConfig().getTeacherMaxCourses();
        if (max <= 0) return null;
        long count = courseService.findAll().stream()
                .filter(c -> teacherId.equals(c.getTeacherId()))
                .filter(c -> excludeCourseId == null || !excludeCourseId.equals(c.getId()))
                .count();
        if (count >= max) {
            User t = userRepository != null ? userRepository.findById(teacherId).orElse(null) : null;
            return "教师「" + (t != null ? t.getName() : teacherId) + "」本学期已授课 " + count
                    + " 门，达到每学期上限 " + max + " 门，请更换教师或为其减课";
        }
        return null;
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        courseService.delete(id);
        return ApiResponse.ok("删除成功", null);
    }

    @GetMapping("/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers() {
        // 脱敏：不返回 password 哈希等敏感字段
        List<Map<String, Object>> out = new ArrayList<>();
        for (User t : courseService.getAllTeachers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("no", t.getNo());
            m.put("name", t.getName());
            m.put("college", t.getCollege());
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    /** 热门课程排行 */
    @GetMapping("/hot")
    public ApiResponse<List<Map<String, Object>>> hotCourses(@RequestParam(defaultValue = "10") int n) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        Map<String, Object> ratings = reviewService.allRatings();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Course cc : courseService.hotCourses(n)) {
            result.add(courseToMap(cc, null, round, ratings));
        }
        return ApiResponse.ok(result);
    }



    private Map<String, Object> courseToMap(Course c, Long userId, int round, Map<String, Object> ratings) {
        return courseToMap(c, userId, round, ratings, enrollmentService.currentSemesterOf(userId));
    }

    private Map<String, Object> courseToMap(Course c, Long userId, int round, Map<String, Object> ratings, Integer semesterNo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("courseCode", c.getCourseCode());
        m.put("hours", c.getHours());
        m.put("teacherId", c.getTeacherId());
        m.put("teacherName", courseService.teacherName(c.getTeacherId()));
        m.put("credit", c.getCredit());
        m.put("capacity", c.getCapacity());
        m.put("category", c.getCategory());
        m.put("time", c.getTime());
        m.put("location", c.getLocation());
        m.put("campus", c.getCampus());
        m.put("examTime", c.getExamTime());
        m.put("intro", c.getIntro());
        m.put("semesterNo", c.getSemesterNo());
        if (c.getPrereqId() != null) {
            Course pre = courseService.findById(c.getPrereqId());
            m.put("prereqId", c.getPrereqId());
            m.put("prereqName", pre != null ? pre.getName() : null);
        }
        m.put("stat", enrollmentService.courseStat(c.getId(), round, semesterNo));
        m.put("oversubscribed", enrollmentService.isOversubscribed(c.getId(), round, semesterNo));
        m.put("full", waitlistService.isFull(c.getId(), round, userId));
        m.put("guide", enrollmentService.pointsGuide(c.getId(), round, semesterNo));
        if (userId != null) {
            var enr = enrollmentService.getEnrollment(userId, c.getId(), round);
            m.put("myStatus", enr != null ? enr.getStatus() : null);
            m.put("myPoints", enr != null ? enr.getPoints() : null);
            var wl = waitlistService.findActive(userId, c.getId(), round).orElse(null);
            m.put("myWaitStatus", wl != null ? wl.getStatus() : null);
            m.put("myWaitPosition", wl != null ? waitlistService.position(wl) : null);
            m.put("myWaitId", wl != null ? wl.getId() : null);
            var it = intentService.find(userId, c.getId());
            m.put("myIntent", it != null);
            m.put("myIntentPoints", it != null ? it.getDesiredPoints() : null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rt = ratings == null ? null : (Map<String, Object>) ratings.get(String.valueOf(c.getId()));
        m.put("rating", rt != null ? rt.get("avg") : null);
        m.put("ratingCount", rt != null ? rt.get("count") : 0);
        return m;
    }
}