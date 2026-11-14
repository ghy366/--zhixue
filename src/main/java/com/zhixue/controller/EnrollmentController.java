package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.User;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.CourseService;
import com.zhixue.service.DropRequestService;
import com.zhixue.service.EnrollmentService;
import com.zhixue.service.GradeService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private DropRequestService dropRequestService;
    @Autowired private CourseService courseService;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private com.zhixue.repository.GraduateAdvisorRepository advisorRepo;
    @Autowired private GradeService gradeService;

    /** 实时选课战况流：本轮最新报名动态（学生姓名脱敏），供课程浏览页滚动展示 */
    @GetMapping("/recent")
    public ApiResponse<List<Map<String, Object>>> recent(@RequestParam(required = false) Integer round,
                                                         @RequestParam(defaultValue = "12") Integer limit) {
        int r = round == null ? enrollmentService.getSystemConfig().getCurrentRound() : round;
        int n = (limit == null || limit <= 0 || limit > 30) ? 12 : limit;
        // 数据库层直接排除必修课（最新一撮 id 多为自动排课批量插入的必修记录）
        List<Enrollment> list = enrollmentRepo.findRecentElectiveByRound(r, org.springframework.data.domain.PageRequest.of(0, n));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Enrollment e : list) {
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;   // 课程已删除的脏记录跳过
            User stu = userRepo.findById(e.getStudentId()).orElse(null);
            String name = stu != null && stu.getName() != null ? stu.getName() : "同学";
            String masked = name.length() <= 1 ? name : name.substring(0, 1) + "**";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseName", c.getName());
            m.put("points", e.getPoints());
            m.put("status", e.getStatus());
            m.put("student", masked);
            out.add(m);
            if (out.size() >= n) break;
        }
        return ApiResponse.ok(out);
    }

    @PostMapping("/enroll")
    public ApiResponse<Void> enroll(@RequestBody Map<String, Object> body,
                                    @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long courseId = Long.valueOf(body.get("courseId").toString());
        int points = Integer.parseInt(body.get("points").toString());
        Map<String, Object> r = enrollmentService.enroll(userId, courseId, points);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/drop")
    public ApiResponse<Void> drop(@RequestBody Map<String, Object> body,
                                  @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Map<String, Object> r = enrollmentService.drop(userId, courseId);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        // 轮次结束后已录取课程：自动向任课老师发起退课申请，学生端显示「请联系本课程的老师进行退课」
        if (Boolean.TRUE.equals(r.get("needTeacher"))) {
            dropRequestService.open(userId, courseId, enrollmentService.getSystemConfig().getCurrentRound());
        }
        return ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/reselect")
    public ApiResponse<Void> reselect(@RequestBody Map<String, Object> body,
                                      @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long oldCourseId = Long.valueOf(body.get("oldCourseId").toString());
        Long newCourseId = Long.valueOf(body.get("newCourseId").toString());
        int points = Integer.parseInt(body.get("points").toString());
        Map<String, Object> r = enrollmentService.reselect(userId, oldCourseId, newCourseId, points);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    /**
     * 换课 Swap（原子互换）：已选课 A ↔ 新课 B。
     * 第 1/2 轮登记保底换课（A 保留、结算时 B 录取才退 A），第 3 轮当场原子互换。
     */
    @PostMapping("/swap")
    public ApiResponse<Void> swap(@RequestBody Map<String, Object> body,
                                  @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        Long fromCourseId = Long.valueOf(body.get("fromCourseId").toString());
        Long toCourseId = Long.valueOf(body.get("toCourseId").toString());
        int points = body.get("points") == null ? 0 : Integer.parseInt(body.get("points").toString());
        Map<String, Object> r = enrollmentService.swap(userId, fromCourseId, toCourseId, points);
        if ((Boolean) r.get("success")) return ApiResponse.ok((String) r.get("msg"), null);
        return ApiResponse.fail((String) r.get("msg"));
    }

    /** 进行中的保底换课列表（新课 pending 且带 swapFrom 标记），供「我的选课」展示换课状态 */
    @GetMapping("/swaps")
    public ApiResponse<List<Map<String, Object>>> swaps(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Enrollment e : enrollmentRepo.findByStudentIdAndRound(userId, round)) {
            if (!"pending".equals(e.getStatus()) || e.getSwapFromCourseId() == null) continue;
            Course b = courseService.findById(e.getCourseId());
            Course a = courseService.findById(e.getSwapFromCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("fromCourseId", e.getSwapFromCourseId());
            m.put("fromCourseName", a != null ? a.getName() : "（课程已删除）");
            m.put("toCourseId", e.getCourseId());
            m.put("toCourseName", b != null ? b.getName() : "（课程已删除）");
            m.put("points", e.getPoints());
            m.put("round", e.getRound());
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader("Authorization") String token,
                                                       @RequestParam(required = false) Integer round,
                                                       @RequestParam(required = false) Integer semester) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (round == null) round = enrollmentService.getSystemConfig().getCurrentRound();
        // 必修课由系统自动排课：读取前先补齐，保证课表/我的课程里一定有必修课
        try { enrollmentService.ensureRequiredEnrollments(userId); } catch (Exception ignored) {}
        int off = enrollmentService.getSystemConfig().getSemesterOffset() == null ? 0 : enrollmentService.getSystemConfig().getSemesterOffset();
        int mySem = com.zhixue.service.GraduationService.currentSemesterNo(
                userRepo.findById(userId).map(User::getGrade).orElse(null), java.time.LocalDate.now(), off);
        // 默认展示本学期；传 semester 参数可查看任意修读学期（课表页学期切换）
        Integer filterSem = semester != null ? semester : mySem;
        List<Enrollment> list = enrollmentRepo.findByStudentIdAndRound(userId, round).stream()
                .filter(e -> !"dropped".equals(e.getStatus()))
                .filter(e -> e.getSemesterNo() == null || e.getSemesterNo() == filterSem)
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : list) {
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;   // 课程已被删除，跳过该脏记录
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("courseId", e.getCourseId());
            m.put("courseName", c != null ? c.getName() : "");
            m.put("category", c != null ? c.getCategory() : "");
            m.put("teacherName", c != null ? courseService.teacherName(c.getTeacherId()) : "");
            m.put("credit", c != null ? c.getCredit() : 0);
            m.put("points", e.getPoints());
            m.put("status", e.getStatus());
            m.put("round", e.getRound());
            m.put("semesterNo", e.getSemesterNo());
            // 毕设课（毕业设计 上/下）显示学生的已确认导师——复用导师双选数据
            if (c.getName() != null && c.getName().contains("毕业设计")) {
                var adv = advisorRepo.findByStudentIdOrderByIdDesc(userId).stream()
                        .filter(a -> "approved".equals(a.getStatus())).findFirst().orElse(null);
                if (adv != null) {
                    User at = userRepo.findById(adv.getTeacherId()).orElse(null);
                    m.put("advisorName", at != null ? at.getName() : null);
                }
            }
            Grade g = gradeService.findByStudentAndCourse(userId, e.getCourseId());
            if (g != null && g.getTotalScore() != null) {
                double sc = g.getTotalScore();
                m.put("grade", sc == Math.floor(sc) ? String.valueOf((long) sc) : String.valueOf(sc));
            } else {
                m.put("grade", null);
            }
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/approved")
    public ApiResponse<List<Map<String, Object>>> approved(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Enrollment> list = enrollmentRepo.findByStudentIdAndRound(userId, round).stream()
                .filter(e -> "approved".equals(e.getStatus())).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : list) {
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("location", c.getLocation());
            m.put("credit", c.getCredit());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /** 选课策略模拟：假设以 points 分报名，按当前快照估算录取概率 */
    @PostMapping("/simulate")
    public ApiResponse<Map<String, Object>> simulate(@RequestBody Map<String, Object> body) {
        Long courseId = Long.valueOf(body.get("courseId").toString());
        int points = Integer.parseInt(body.get("points").toString());
        return ApiResponse.ok(enrollmentService.simulateAdmission(courseId, points));
    }

    @GetMapping("/points")
    public ApiResponse<Map<String, Object>> points(@RequestHeader("Authorization") String token) {        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        Map<String, Object> m = new HashMap<>();
        m.put("round", round);
        m.put("total", EnrollmentService.POINTS_PER_ROUND);
        m.put("used", enrollmentService.getUsedPoints(userId, round));
        m.put("remaining", enrollmentService.getRemainingPoints(userId, round));
        return ApiResponse.ok(m);
    }

    /** 当前学期序（含学期滚动偏移）——前端学期切换的单一事实来源 */
    @GetMapping("/my-semester")
    public ApiResponse<Map<String, Object>> mySemester(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (u == null) return ApiResponse.fail("登录已过期，请重新登录");
        int off = enrollmentService.getSystemConfig().getSemesterOffset() == null ? 0 : enrollmentService.getSystemConfig().getSemesterOffset();
        int no = com.zhixue.service.GraduationService.currentSemesterNo(u.getGrade(), java.time.LocalDate.now(), off);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("semesterNo", no);
        m.put("label", com.zhixue.service.GraduationService.semesterLabel(u.getGrade(), no));
        return ApiResponse.ok(m);
    }

    @GetMapping("/scores")
    public ApiResponse<List<Map<String, Object>>> scores(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        int curRound = enrollmentService.getSystemConfig().getCurrentRound();
        // 本轮有报名（待录取/已录取）的课程集合：挂科课若在其中即处于「重修中」
        Set<Long> inProgress = enrollmentRepo.findByStudentIdAndRound(userId, curRound).stream()
                .filter(e -> "approved".equals(e.getStatus()) || "pending".equals(e.getStatus()))
                .map(Enrollment::getCourseId)
                .collect(java.util.stream.Collectors.toSet());
        // 同一门课可能存在多条历史轮次记录：按课程去重，优先保留已有成绩的那条
        Map<Long, Enrollment> byCourse = new LinkedHashMap<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(userId)) {
            if (!"approved".equals(e.getStatus())) continue;
            Enrollment prev = byCourse.get(e.getCourseId());
            if (prev == null) { byCourse.put(e.getCourseId(), e); continue; }
            boolean curScored = gradeService.findByStudentAndCourse(userId, e.getCourseId()) != null;
            if (curScored) byCourse.put(e.getCourseId(), e);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : byCourse.values()) {
            Course c = courseService.findById(e.getCourseId());
            if (c == null) continue;
            Grade g = gradeService.findByStudentAndCourse(userId, c.getId());
            boolean failed = g != null && g.getTotalScore() != null && g.getTotalScore() < 60;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("credit", c.getCredit());
            m.put("round", e.getRound());
            m.put("semesterNo", e.getSemesterNo());
            m.put("regularScore", g != null ? g.getRegularScore() : null);
            m.put("finalScore", g != null ? g.getFinalScore() : null);
            m.put("totalScore", g != null ? g.getTotalScore() : null);
            m.put("examType", g != null ? g.getExamType() : null);
            m.put("attempt", g != null ? g.getAttempt() : null);
            Integer sem = g != null ? g.getSemesterNo() : null;
            m.put("semesterNo", sem);
            m.put("semesterLabel", sem != null
                    ? com.zhixue.service.GraduationService.semesterLabel(
                        userRepo.findById(userId).map(User::getGrade).orElse(null), sem)
                    : null);
            // 重修状态：挂科（failed）且本轮有报名 = 重修中；挂科且本轮未报名 = 待补考/待重修
            m.put("failed", failed);
            m.put("retaking", failed && inProgress.contains(c.getId()));
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /**
     * 某课程的报名学生名单（报名记录，热门课可达数百条）。传 page/size 时返回 {items,total,page,size}，
     * 未传时保持旧的全量数组（旧调用方零破坏）。keyword 匹配 学号/姓名/班级/专业。
     */
    @GetMapping("/course/{courseId}")
    public ApiResponse<Object> courseEnrollments(@PathVariable Long courseId,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) String keyword) {
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Enrollment> list = enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> !"dropped".equals(e.getStatus()))
                .sorted((a, b) -> b.getPoints() - a.getPoints())
                .toList();
        List<Map<String, Object>> all = new ArrayList<>();
        for (Enrollment e : list) {
            User stu = userRepo.findById(e.getStudentId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("studentId", e.getStudentId());
            m.put("studentName", stu != null ? stu.getName() : "未知");
            m.put("studentNo", stu != null ? stu.getNo() : "");
            m.put("studentMajor", stu != null ? stu.getMajor() : "");
            m.put("studentClass", stu != null ? stu.getClassNo() : "");
            m.put("studentGrade", stu != null ? stu.getGrade() : null);
            m.put("studentPhone", stu != null ? stu.getPhone() : "");
            m.put("points", e.getPoints());
            m.put("status", e.getStatus());
            all.add(m);
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            all = all.stream().filter(m -> {
                String hay = (m.get("studentNo") + " " + m.get("studentName") + " "
                        + m.get("studentClass") + " " + m.get("studentMajor")).toString().toLowerCase();
                return hay.contains(k);
            }).toList();
        }
        if (page == null || size == null || page < 1 || size < 1) {
            return ApiResponse.ok(all);   // 兼容模式：StudentList 需全量做投分分布统计
        }
        int from = Math.min(all.size(), (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", all.subList(from, to));
        data.put("total", all.size());
        data.put("page", page);
        data.put("size", size);
        return ApiResponse.ok(data);
    }

    @PostMapping("/settle")
    public ApiResponse<Map<String, Object>> settle(@RequestBody(required = false) Map<String, Object> body) {
        Integer round = (body == null || body.get("round") == null)
                ? null : Integer.parseInt(body.get("round").toString());
        // 结算只允许针对「当前已解锁且进行中」的轮次，防止跳级结算第 2/3 轮
        com.zhixue.entity.SystemConfig cfg = enrollmentService.getSystemConfig();
        int cur = cfg.getCurrentRound() == null ? 1 : cfg.getCurrentRound();
        int progress = cfg.getRoundProgress() == null ? 0 : cfg.getRoundProgress();
        int unlocked = Math.min(3, progress + 1);
        if (round == null) round = cur;
        if (round != cur) return ApiResponse.fail("只能结算当前进行中的第 " + cur + " 轮");
        if (round > unlocked) return ApiResponse.fail("第 " + round + " 轮尚未解锁，不能结算");
        if (round >= 3) return ApiResponse.fail("第 3 轮为先到先得，无需结算");
        Map<String, Object> r = enrollmentService.settleRound(round);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        return ApiResponse.ok((String) r.get("msg"), data);
    }

    @PostMapping("/enroll-batch")
    public ApiResponse<Map<String, Object>> enrollBatch(@RequestBody Map<String, Object> body,
                                                         @RequestHeader("Authorization") String token) {
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("未登录");
        List<?> raw = (List<?>) body.get("courseIds");
        List<Long> ids = new java.util.ArrayList<>();
        if (raw != null) { for (Object o : raw) ids.add(Long.valueOf(o.toString())); }
        int points = body.get("points") == null ? 1 : Integer.parseInt(body.get("points").toString());
        return ApiResponse.ok(enrollmentService.enrollBatch(uid, ids, points));
    }

    @PostMapping("/drop-batch")
    public ApiResponse<Map<String, Object>> dropBatch(@RequestBody Map<String, Object> body,
                                                       @RequestHeader("Authorization") String token) {
        Long uid = com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (uid == null) return ApiResponse.fail("未登录");
        List<?> raw = (List<?>) body.get("courseIds");
        List<Long> ids = new java.util.ArrayList<>();
        if (raw != null) { for (Object o : raw) ids.add(Long.valueOf(o.toString())); }
        Map<String, Object> r = enrollmentService.dropBatch(uid, ids);
        // 批量退课里被「需老师处理」拦下的课程，同样逐条生成申请
        if (r.get("data") instanceof List<?> details) {
            for (Object o : details) {
                if (o instanceof Map<?, ?> item && Boolean.TRUE.equals(item.get("needTeacher"))) {
                    Object cid = item.get("courseId");
                    if (cid != null) dropRequestService.open(uid, Long.valueOf(cid.toString()),
                            enrollmentService.getSystemConfig().getCurrentRound());
                }
            }
        }
        if (!Boolean.TRUE.equals(r.get("success"))) return ApiResponse.fail((String) r.get("msg"));
        return ApiResponse.ok(r);
    }
}
