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
    @Autowired private com.zhixue.repository.CourseRepository courseRepo;
    @Autowired private com.zhixue.repository.SystemConfigRepository systemConfigRepo;
    @Autowired private com.zhixue.repository.GraduateAdvisorRepository advisorRepo;
    @Autowired private GradeService gradeService;
    @Autowired private com.zhixue.service.GradeLedgerService ledger;
    @Autowired private com.zhixue.repository.TeachingClassRepository teachingClassRepo;

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
        // 必修课由系统自动排课：读取前先补齐，保证课表/我的课程里一定有必修课
        try { enrollmentService.ensureRequiredEnrollments(userId); } catch (Exception ignored) {}
        int off = enrollmentService.getSystemConfig().getSemesterOffset() == null ? 0 : enrollmentService.getSystemConfig().getSemesterOffset();
        int mySem = com.zhixue.service.GraduationService.currentSemesterNo(
                userRepo.findById(userId).map(User::getGrade).orElse(null), userRepo.findById(userId).map(User::getMajor).orElse(null), java.time.LocalDate.now(), off);
        // 默认展示本学期；传 semester 参数可查看任意修读学期（课表页学期切换）
        Integer filterSem = semester != null ? semester : mySem;
        // 学期视图（带 semester 且未指定 round）合并该学期全部轮次；
        // 指定 round 时才按单轮取数，供三轮选课轨迹分轮展示
        List<Enrollment> source = (semester != null && round == null)
                ? enrollmentRepo.findByStudentId(userId)
                : enrollmentRepo.findByStudentIdAndRound(userId,
                        round == null ? enrollmentService.getSystemConfig().getCurrentRound() : round);
        // 同一门课可能有多轮记录（换课/重修）：按状态优先级去重，避免重复统计学分
        Map<Long, Enrollment> byCourse = new LinkedHashMap<>();
        for (Enrollment e : source) {
            if ("dropped".equals(e.getStatus())) continue;
            if (e.getSemesterNo() != null && !e.getSemesterNo().equals(filterSem)) continue;
            Enrollment prev = byCourse.get(e.getCourseId());
            if (prev == null || statusRank(e.getStatus()) > statusRank(prev.getStatus())) {
                byCourse.put(e.getCourseId(), e);
            }
        }
        List<Enrollment> list = new ArrayList<>(byCourse.values());
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
            m.put("source", e.getSource());
            // 组班重修：课表按教学班自己的时间/地点/教师展示（不是原课程的上课安排）
            if (e.getTeachingClassId() != null) {
                var tc = teachingClassRepo.findById(e.getTeachingClassId()).orElse(null);
                if (tc != null) {
                    m.put("teachingClassId", tc.getId());
                    m.put("teachingClassNo", tc.getClassNo());
                    m.put("time", tc.getTime());
                    m.put("location", tc.getLocation());
                    if (tc.getTeacherId() != null) m.put("teacherName", courseService.teacherName(tc.getTeacherId()));
                }
            }
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
        int no = com.zhixue.service.GraduationService.currentSemesterNo(u.getGrade(), u.getMajor(), java.time.LocalDate.now(), off);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("semesterNo", no);
        m.put("label", com.zhixue.service.GraduationService.semesterLabel(u.getGrade(), no));
        return ApiResponse.ok(m);
    }

    /** 学期视图去重用的状态优先级：录取 > 待审 > 候补 > 落选 */
    private static int statusRank(String status) {
        return switch (status == null ? "" : status) {
            case "approved" -> 4;
            case "pending" -> 3;
            case "waitlist" -> 2;
            case "rejected" -> 1;
            default -> 0;
        };
    }

    /** 我的学期列表：有选课记录或有成绩的学期 + 当前学期（倒序），供「我的选课」学期切换使用 */
    @GetMapping("/my-semesters")
    public ApiResponse<List<Map<String, Object>>> mySemesters(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId != null ? userRepo.findById(userId).orElse(null) : null;
        if (u == null) return ApiResponse.fail("登录已过期，请重新登录");
        int off = enrollmentService.getSystemConfig().getSemesterOffset() == null ? 0 : enrollmentService.getSystemConfig().getSemesterOffset();
        int cur = com.zhixue.service.GraduationService.currentSemesterNo(u.getGrade(), u.getMajor(), java.time.LocalDate.now(), off);
        TreeSet<Integer> nos = new TreeSet<>();
        // 未来学期不进入学期列表：学生看不到以后的课表（真实教务口径），
        // 自动排课写入的未来学期选课记录只供毕业进度/培养方案视图使用
        for (Enrollment e : enrollmentRepo.findByStudentId(userId)) {
            if (e.getSemesterNo() != null && e.getSemesterNo() <= cur && !"dropped".equals(e.getStatus())) nos.add(e.getSemesterNo());
        }
        for (Grade g : gradeService.findByStudent(userId)) {
            if (g.getSemesterNo() != null && g.getSemesterNo() <= cur) nos.add(g.getSemesterNo());
        }
        nos.add(cur);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Integer no : nos.descendingSet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("semesterNo", no);
            m.put("label", com.zhixue.service.GraduationService.semesterLabel(u.getGrade(), no));
            m.put("current", no == cur);
            out.add(m);
        }
        return ApiResponse.ok(out);
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
            // 学期归属：成绩没写学期时退回选课记录的学期，避免该课在所有学期都统计不到
            Integer sem = g != null && g.getSemesterNo() != null ? g.getSemesterNo() : e.getSemesterNo();
            m.put("semesterNo", sem);
            m.put("semesterLabel", sem != null
                    ? com.zhixue.service.GraduationService.semesterLabel(
                        userRepo.findById(userId).map(User::getGrade).orElse(null), sem)
                    : null);
            // 重修状态：挂科（failed）且本轮有报名 = 重修中；挂科且本轮未报名 = 待补考/待重修
            m.put("failed", failed);
            m.put("retaking", failed && inProgress.contains(c.getId()));
            // 成绩流水：latest（= grades 视图）+ 历次修读记录，用于学生端抽屉与状态徽章
            var records = ledger.history(userId, c.getId());
            m.put("latest", records.isEmpty() ? null : records.get(records.size() - 1));
            m.put("history", records.stream().map(r -> {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("attemptNo", r.getAttemptNo());
                h.put("examType", r.getExamType());
                h.put("regularScore", r.getRegularScore());
                h.put("finalScore", r.getFinalScore());
                h.put("totalScore", r.getTotalScore());
                h.put("semesterNo", r.getSemesterNo());
                h.put("recordedAt", r.getRecordedAt());
                return h;
            }).toList());
            var makeup = ledger.activeMakeup(userId, c.getId()).orElse(null);
            var retake = ledger.activeRetake(userId, c.getId()).orElse(null);
            Map<String, Object> mk = null;
            if (makeup != null) {
                mk = new LinkedHashMap<>();
                mk.put("id", makeup.getId());
                mk.put("status", makeup.getStatus());
                mk.put("attemptNo", makeup.getAttemptNo());
                mk.put("score", makeup.getScore());
                mk.put("examTime", makeup.getExamTime());
                mk.put("location", makeup.getLocation());
                mk.put("registeredAt", makeup.getRegisteredAt());
            }
            Map<String, Object> rt = null;
            if (retake != null) {
                rt = new LinkedHashMap<>();
                rt.put("id", retake.getId());
                rt.put("status", retake.getStatus());
                rt.put("attemptNo", retake.getAttemptNo());
                rt.put("form", retake.getForm());
                rt.put("targetClassId", retake.getTargetClassId());
                rt.put("score", retake.getScore());
            }
            m.put("makeup", mk);
            m.put("retake", rt);
            // 6 类状态徽章：待补考 / 补考已报名 / 待重修 / 重修中 / 已通过·补考 / 已通过·重修N
            m.put("badge", badgeOf(g, makeup, retake));
            result.add(m);
        }
        // 历史成绩可能没有对应的选课记录（培养方案调整前的课程、方案外选修等）：
        // 成绩档案以成绩为准并入这些行，避免学生成绩页丢课程
        java.util.Set<Long> covered = new java.util.HashSet<>();
        for (Map<String, Object> m : result) covered.add((Long) m.get("courseId"));
        for (Grade g : gradeService.findByStudent(userId)) {
            if (g.getCourseId() == null || covered.contains(g.getCourseId())) continue;
            Course c = courseService.findById(g.getCourseId());
            if (c == null) continue;
            boolean failed = g.getTotalScore() != null && g.getTotalScore() < 60;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("credit", c.getCredit());
            m.put("round", null);
            m.put("regularScore", g.getRegularScore());
            m.put("finalScore", g.getFinalScore());
            m.put("totalScore", g.getTotalScore());
            m.put("examType", g.getExamType());
            m.put("attempt", g.getAttempt());
            Integer sem = g.getSemesterNo();
            m.put("semesterNo", sem);
            m.put("semesterLabel", sem != null
                    ? com.zhixue.service.GraduationService.semesterLabel(
                        userRepo.findById(userId).map(User::getGrade).orElse(null), sem)
                    : null);
            m.put("failed", failed);
            m.put("retaking", false);
            var records = ledger.history(userId, c.getId());
            m.put("latest", records.isEmpty() ? null : records.get(records.size() - 1));
            m.put("history", records.stream().map(r -> {
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("attemptNo", r.getAttemptNo());
                h.put("examType", r.getExamType());
                h.put("regularScore", r.getRegularScore());
                h.put("finalScore", r.getFinalScore());
                h.put("totalScore", r.getTotalScore());
                h.put("semesterNo", r.getSemesterNo());
                h.put("recordedAt", r.getRecordedAt());
                return h;
            }).toList());
            var makeup = ledger.activeMakeup(userId, c.getId()).orElse(null);
            var retake = ledger.activeRetake(userId, c.getId()).orElse(null);
            Map<String, Object> mk = null;
            if (makeup != null) {
                mk = new LinkedHashMap<>();
                mk.put("id", makeup.getId());
                mk.put("status", makeup.getStatus());
                mk.put("attemptNo", makeup.getAttemptNo());
                mk.put("score", makeup.getScore());
                mk.put("examTime", makeup.getExamTime());
                mk.put("location", makeup.getLocation());
                mk.put("registeredAt", makeup.getRegisteredAt());
            }
            Map<String, Object> rt = null;
            if (retake != null) {
                rt = new LinkedHashMap<>();
                rt.put("id", retake.getId());
                rt.put("status", retake.getStatus());
                rt.put("attemptNo", retake.getAttemptNo());
                rt.put("form", retake.getForm());
                rt.put("targetClassId", retake.getTargetClassId());
                rt.put("score", retake.getScore());
            }
            m.put("makeup", mk);
            m.put("retake", rt);
            m.put("badge", badgeOf(g, makeup, retake));
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /** 成绩行状态徽章（前端按 code 渲染六类文案，避免文案漂移） */
    private String badgeOf(Grade g, com.zhixue.entity.MakeupExam m, com.zhixue.entity.RetakeApply r) {
        if (r != null) {
            switch (r.getStatus()) {
                case "PASSED": return "retake_passed";
                // 已报名/已同意但未进班：只算“待教务安排”，不能显示为重修中，也不能自助选跟班
                case "APPLIED", "APPROVED": return "retake_registered";
                case "ENROLLED": return "retaking";
                case "ELIGIBLE": return "retake_todo";
                default: break;
            }
        }
        if (m != null) {
            switch (m.getStatus()) {
                case "PASSED": return "makeup_passed";
                case "REGISTERED", "SCHEDULED": return "makeup_registered";
                case "ELIGIBLE": return "makeup_todo";
                default: break;
            }
        }
        return "";
    }

    /**
     * 某课程的报名学生名单（报名记录，热门课可达数百条）。传 page/size 时返回 {items,total,page,size}，
     * 未传时保持旧的全量数组（旧调用方零破坏）。keyword 匹配 学号/姓名/班级/专业。
     */
    @GetMapping("/course/{courseId}")
    public ApiResponse<Object> courseEnrollments(@PathVariable Long courseId,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestHeader(value = "Authorization", required = false) String token) {
        // 教师只能查看本人任教课程的报名名单（含手机号）；管理员放行
        Long operatorId = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        if (operatorId == null) return ApiResponse.fail("登录已过期，请重新登录");
        User operator = userRepo.findById(operatorId).orElse(null);
        if (operator != null && "teacher".equals(operator.getRole())) {
            com.zhixue.entity.Course course = courseRepo.findById(courseId).orElse(null);
            if (course == null) return ApiResponse.fail("课程不存在");
            if (!operatorId.equals(course.getTeacherId())) return ApiResponse.fail("无权访问非本人任教课程的报名名单");
        }
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
            m.put("source", e.getSource());   // retake = 跟班重修学生
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
        // 结算成功后同步「关闭本轮 + 推进进度」，与手动关闭语义一致：
        // 否则 roundOpen 仍 true，学生可继续向已结算轮次报名挂 pending，且下一轮仍锁死。
        com.zhixue.entity.SystemConfig cfg2 = systemConfigRepo.findById(1L).orElse(null);
        if (cfg2 != null) {
            cfg2.setRoundOpen(false);
            if (round > progress) cfg2.setRoundProgress(round);
            systemConfigRepo.save(cfg2);
        }
        return ApiResponse.ok((String) r.get("msg") + "，本轮已关闭并解锁下一轮", data);
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
