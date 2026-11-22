package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.Course;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.GradeRecord;
import com.zhixue.entity.MakeupExam;
import com.zhixue.entity.RetakeApply;
import com.zhixue.entity.User;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.TeachingClassRepository;
import com.zhixue.entity.TeachingClass;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.MakeupExamRepository;
import com.zhixue.repository.RetakeApplyRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.EnrollmentService;
import com.zhixue.service.GradeLedgerService;
import com.zhixue.service.GradeService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/grades")
public class GradeController {

    @Autowired private GradeService gradeService;
    @Autowired private UserRepository userRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private GradeLedgerService ledger;
    @Autowired private MakeupExamRepository makeupRepo;
    @Autowired private RetakeApplyRepository retakeRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private com.zhixue.repository.TeachingClassRepository teachingClassRepo;

    /**
     * 老师查看某课程的学生成绩。
     * batch=normal（默认）本轮已录取；makeup 补考册学生；retake 重修录取学生——
     * 三个 tab 的名单来源不同，不能共用同一份选课名单。
     */
    @GetMapping("/course/{courseId}")
    public ApiResponse<List<Map<String, Object>>> courseGrades(@PathVariable Long courseId,
                                                               @RequestParam(required = false, defaultValue = "normal") String batch,
                                                               @RequestParam(required = false) Long classId,
                                                               @RequestHeader(value = "Authorization", required = false) String token) {
        // 教师只能查看本人任教课程的名单/成绩；管理员放行
        String deny = checkTeacherCourseOwner(courseId, token);
        if (deny != null) return ApiResponse.fail(deny);
        List<Map<String, Object>> result = new ArrayList<>();
        if ("makeup".equalsIgnoreCase(batch)) {
            Set<Long> seen = new LinkedHashSet<>();
            Set<String> allowed = Set.of("REGISTERED", "SCHEDULED", "PASSED", "FAILED", "ABSENT");
            for (MakeupExam m : makeupRepo.findAll()) {
                if (!courseId.equals(m.getCourseId()) || !allowed.contains(m.getStatus())) continue;
                if (!seen.add(m.getStudentId())) continue;
                result.add(gradeRow(m.getStudentId(), courseId, "batch", m.getStatus()));
            }
            return ApiResponse.ok(result);
        }
        if ("retake".equalsIgnoreCase(batch)) {
            Set<Long> seen = new LinkedHashSet<>();
            Set<String> allowed = Set.of("APPROVED", "ENROLLED", "PASSED", "FAILED");
            for (RetakeApply r : retakeRepo.findAll()) {
                if (!courseId.equals(r.getCourseId()) || !allowed.contains(r.getStatus())) continue;
                if (!seen.add(r.getStudentId())) continue;
                result.add(gradeRow(r.getStudentId(), courseId, "batch", r.getStatus()));
            }
            return ApiResponse.ok(result);
        }
        int round = enrollmentService.getSystemConfig().getCurrentRound();
        List<Enrollment> enrolls = enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .sorted(Comparator.comparing(Enrollment::getId))
                .toList();
        // 教学班维度过滤：同一课程开出多个平行班时，学生按学号排序后均分到各平行班
        // （班级序号决定切片，稳定且可解释）——否则全校通识课（如形势与政策）会把
        // 所有平行班的学生都列进一个教学班的名单里。
        enrolls = splitByTeachingClass(enrolls, courseId, classId);
        for (Enrollment e : enrolls) {
            result.add(gradeRow(e.getStudentId(), courseId, null, null));
        }
        return ApiResponse.ok(result);
    }

    /**
     * 平行班名单切分：同一课程开出多个平行班（教学执行计划）时，学生按学号排序后
     * 均分到各班，班级序号决定切片。确定性的分配——同一学生同一轮次永远落在同一个班，
     * 且贴近真实教务「按学号分班」的做法。classId 为空 / 教学班与课程不匹配 / 仅一个平行班时不切分。
     */
    private List<Enrollment> splitByTeachingClass(List<Enrollment> enrolls, Long courseId, Long classId) {
        if (classId == null || enrolls.size() <= 1) return enrolls;
        TeachingClass tc = teachingClassRepo.findById(classId).orElse(null);
        if (tc == null || !courseId.equals(tc.getCourseId())) return enrolls;
        // 排课/组班时已把学生写进教学班（enrollment.teachingClassId）：直接按已存成员取名单，
        // 与「学生名册」页（/teaching-classes/{id}/members）完全一致。
        // 若再按学号均切，会把名册 45 人切成 32 人，名册与成绩录入两页口径打架。
        List<Enrollment> assigned = enrollmentRepo.findByTeachingClassIdOrderByIdAsc(classId).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .toList();
        if (!assigned.isEmpty()) return assigned;
        // 兜底：历史班级没有已存成员分配时，才按学号均分到各平行班
        List<TeachingClass> parallels = teachingClassRepo.findByCourseIdAndStatusOrderByClassNoAsc(courseId, "OPEN");
        if (parallels.size() <= 1) return enrolls;
        // 学号映射（用于稳定排序）
        Map<Long, String> noMap = new HashMap<>();
        for (User u : userRepo.findAllById(enrolls.stream().map(Enrollment::getStudentId).toList())) {
            noMap.put(u.getId(), u.getNo() == null ? "~" : u.getNo());
        }
        List<Enrollment> sorted = new ArrayList<>(enrolls);
        sorted.sort(Comparator.comparing(e -> noMap.getOrDefault(e.getStudentId(), "~")));
        int idx = 0;
        for (int i = 0; i < parallels.size(); i++) {
            if (classId.equals(parallels.get(i).getId())) { idx = i; break; }
        }
        int size = parallels.size();
        int base = sorted.size() / size;
        int extra = sorted.size() % size;
        int from = idx * base + Math.min(idx, extra);
        int to = from + base + (idx < extra ? 1 : 0);
        return sorted.subList(Math.min(from, sorted.size()), Math.min(to, sorted.size()));
    }

    private Map<String, Object> gradeRow(Long studentId, Long courseId, String flag, String flowStatus) {
        User u = userRepo.findById(studentId).orElse(null);
        Grade g = gradeService.findByStudentAndCourse(studentId, courseId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gradeId", g != null ? g.getId() : null);
        m.put("studentId", studentId);
        m.put("studentNo", u != null ? u.getNo() : "");
        m.put("studentName", u != null ? u.getName() : "未知");
        m.put("studentMajor", u != null ? u.getMajor() : "");
        m.put("studentClass", u != null ? u.getClassNo() : "");
        m.put("regularScore", g != null ? g.getRegularScore() : null);
        m.put("finalScore", g != null ? g.getFinalScore() : null);
        m.put("totalScore", g != null ? g.getTotalScore() : null);
        m.put("examType", g != null ? g.getExamType() : null);
        m.put("attempt", g != null ? g.getAttempt() : null);
        m.put("flowStatus", flowStatus);
        return m;
    }

    /** 成绩流水：学生查自己的历次修读（正考 → 补考 → 重修） */
    @GetMapping("/mine/records")
    public ApiResponse<Map<String, Object>> myRecords(@RequestHeader("Authorization") String token,
                                                      @RequestParam(required = false) Long courseId) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        return ApiResponse.ok(recordsOf(userId, courseId));
    }

    /** 成绩流水：教师/管理员查某学生（补考、重修录分前核对历次成绩） */
    @GetMapping("/student/{studentId}/records")
    public ApiResponse<Map<String, Object>> studentRecords(@PathVariable Long studentId,
                                                           @RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(recordsOf(studentId, courseId));
    }

    private Map<String, Object> recordsOf(Long studentId, Long courseId) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (courseId == null) {
            out.put("history", ledgerHistory(studentId));
            return out;
        }
        List<GradeRecord> list = ledger.history(studentId, courseId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GradeRecord r : list) rows.add(recordRow(r));
        out.put("courseId", courseId);
        out.put("latest", list.isEmpty() ? null : rows.get(rows.size() - 1));
        out.put("history", rows);
        return out;
    }

    private List<Map<String, Object>> ledgerHistory(Long studentId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long cid : courseIdsOf(studentId)) {
            List<GradeRecord> hist = ledger.history(studentId, cid);
            if (hist.isEmpty()) continue;
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("courseId", cid);
            g.put("history", hist.stream().map(this::recordRow).toList());
            rows.add(g);
        }
        return rows;
    }

    private Set<Long> courseIdsOf(Long studentId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Grade g : gradeService.findByStudent(studentId)) ids.add(g.getCourseId());
        for (MakeupExam m : makeupRepo.findByStudentIdOrderByIdDesc(studentId)) ids.add(m.getCourseId());
        for (RetakeApply r : retakeRepo.findByStudentIdOrderByIdDesc(studentId)) ids.add(r.getCourseId());
        return ids;
    }

    private Map<String, Object> recordRow(GradeRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("attemptNo", r.getAttemptNo());
        m.put("examType", r.getExamType());
        m.put("regularScore", r.getRegularScore());
        m.put("finalScore", r.getFinalScore());
        m.put("totalScore", r.getTotalScore());
        m.put("semesterNo", r.getSemesterNo());
        m.put("recordedAt", r.getRecordedAt());
        return m;
    }

    // 老师录入/更新成绩（examType：normal 正常 / makeup 补考 / retake 重修；留空自动推断——已有成绩再录视为补考）
    @PostMapping
    public ApiResponse<Grade> saveGrade(@RequestBody Map<String, Object> body,
                                        @RequestHeader(value = "Authorization", required = false) String token) {
        Long studentId = Long.valueOf(body.get("studentId").toString());
        Long courseId = Long.valueOf(body.get("courseId").toString());
        // 教师只能录入本人任教课程的成绩；管理员可代为录入
        Long operatorId = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        if (operatorId == null) return ApiResponse.fail("登录已过期，请重新登录");
        User operator = userRepo.findById(operatorId).orElse(null);
        Long classId = body.get("classId") != null ? Long.valueOf(body.get("classId").toString()) : null;
        if (operator != null && "teacher".equals(operator.getRole())) {
            // 教学班教师按班校验归属；否则按课程负责人校验
            if (classId != null) {
                TeachingClass tc = teachingClassRepo.findById(classId).orElse(null);
                if (tc == null) return ApiResponse.fail("教学班不存在");
                if (!operatorId.equals(tc.getTeacherId())) {
                    return ApiResponse.fail("无权录入非本人教学班的成绩");
                }
            } else {
                Course course = courseRepo.findById(courseId).orElse(null);
                if (course == null) return ApiResponse.fail("课程不存在");
                if (!operatorId.equals(course.getTeacherId())) {
                    return ApiResponse.fail("无权录入非本人任教课程的成绩");
                }
            }
        }
        Double regular = body.get("regularScore") != null ? Double.valueOf(body.get("regularScore").toString()) : null;
        Double finalScore = body.get("finalScore") != null ? Double.valueOf(body.get("finalScore").toString()) : null;
        String examType = body.get("examType") != null ? body.get("examType").toString() : null;
        String type = GradeLedgerService.normalizeType(examType);
        if ("makeup".equals(type) && ledger.activeMakeup(studentId, courseId).isEmpty()) {
            return ApiResponse.fail("该学生本课程没有待处理的补考记录，不能按补考录分");
        }
        if ("retake".equals(type) && ledger.activeRetake(studentId, courseId).isEmpty()) {
            return ApiResponse.fail("该学生本课程没有重修记录，不能按重修录分");
        }
        Grade g = gradeService.save(studentId, courseId, regular, finalScore, examType);
        String msg = "成绩已保存";
        if ("makeup".equals(type) && g.getTotalScore() != null) {
            Integer cap = enrollmentService.getSystemConfig().getMakeupScoreCap();
            if (cap != null && cap > 0 && cap < 100) msg += "（补考按 " + cap + " 分封顶）";
        }
        return ApiResponse.ok(msg, g);
    }

    // 学生查看自己的成绩
    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> myGrades(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        List<Grade> grades = gradeService.findByStudent(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Grade g : grades) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", g.getCourseId());
            m.put("regularScore", g.getRegularScore());
            m.put("finalScore", g.getFinalScore());
            m.put("totalScore", g.getTotalScore());
            m.put("examType", g.getExamType());
            m.put("attempt", g.getAttempt());
            result.add(m);
        }
        return ApiResponse.ok(result);
    }

    /** 教师课程归属校验：教师只能访问本人任教课程，管理员放行；返回 null 表示通过，否则为拒绝原因 */
    public String checkTeacherCourseOwner(Long courseId, String token) {
        Long operatorId = token == null ? null : com.zhixue.util.SessionStore.getUserId(token.replace("Bearer ", ""));
        if (operatorId == null) return "登录已过期，请重新登录";
        User operator = userRepo.findById(operatorId).orElse(null);
        if (operator == null) return "登录状态无效";
        if ("admin".equals(operator.getRole())) return null;   // 管理员总览放行
        if ("teacher".equals(operator.getRole())) {
            Course course = courseRepo.findById(courseId).orElse(null);
            if (course == null) return "课程不存在";
            if (operatorId.equals(course.getTeacherId())) return null;
            // 平行班机制：同一门课拆出的教学班可分派给多位教师（teaching_class.teacher_id），
            // 教师按"班"认课。只按 course.teacher_id 校验会把带了平行班却不是课程主讲教师的
            // 老师误判成"非本人任教"→ 名册/成绩页 0 人。任教该课任一教学班即放行。
            boolean teachesClass = teachingClassRepo.findByTeacherIdOrderByIdDesc(operatorId).stream()
                    .anyMatch(tc -> courseId.equals(tc.getCourseId()));
            if (!teachesClass) return "无权访问非本人任教课程的名单/成绩";
        }
        return null;
    }
}
