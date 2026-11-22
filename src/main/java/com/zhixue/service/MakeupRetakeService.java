package com.zhixue.service;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 补考 / 重修业务：学生报名、教务排考与定形式、跟班重修写入课表、统计口径。
 * 状态流转本身由 {@link GradeLedgerService} 在成绩保存时推进，本服务只负责流程操作。
 */
@Service
public class MakeupRetakeService {

    @Autowired private MakeupExamRepository makeupRepo;
    @Autowired private RetakeApplyRepository retakeRepo;
    @Autowired private GradeRecordRepository recordRepo;
    @Autowired private GradeRepository gradeRepo;
    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private GradeLedgerService ledger;
    @Autowired private EnrollmentService enrollmentService;
    @Autowired private TeachingClassRepository teachingClassRepo;
    @Autowired private ExcelService excelService;

    /** 学生端「我的补考」 */
    public List<Map<String, Object>> myMakeups(Long studentId) {
        SystemConfig cfg = config();
        List<Map<String, Object>> out = new ArrayList<>();
        for (MakeupExam m : makeupRepo.findByStudentIdOrderByIdDesc(studentId)) {
            if ("CANCELLED".equals(m.getStatus())) continue;
            Map<String, Object> row = makeupRow(m);
            row.put("canRegister", canRegister(m, cfg));
            row.put("windowOpen", windowOpen(cfg));
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> registerMakeup(Long studentId, Long makeupId) {
        MakeupExam m = makeupRepo.findById(makeupId)
                .orElseThrow(() -> new IllegalStateException("补考记录不存在"));
        if (!studentId.equals(m.getStudentId())) throw new IllegalStateException("无权操作他人的补考记录");
        if (!"ELIGIBLE".equals(m.getStatus())) throw new IllegalStateException("当前状态不可报名：" + statusCn(m.getStatus()));
        if (!windowOpen(config())) throw new IllegalStateException("补考报名窗口未开放或已结束");
        m.setStatus("REGISTERED");
        m.setRegisteredAt(LocalDateTime.now());
        makeupRepo.save(m);
        return makeupRow(m);
    }

    /** 教务端补考册列表 */
    public List<Map<String, Object>> listMakeups(String status, Long courseId, Integer semesterNo) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MakeupExam m : makeupRepo.findAll().stream()
                .sorted(Comparator.comparing(MakeupExam::getId).reversed()).toList()) {
            if (status != null && !status.isBlank() && !status.equals(m.getStatus())) continue;
            if (courseId != null && !courseId.equals(m.getCourseId())) continue;
            if (semesterNo != null && !semesterNo.equals(m.getSemesterNo())) continue;
            out.add(makeupRow(m));
        }
        return out;
    }

    /** 一键生成补考册：正考 < 60 且尚无补考记录的学生课程补齐一条 ELIGIBLE。
     *  semesterLabel 为「学期设置」里的学期名（按学生年级换算），优先于 semesterNo。 */
    @Transactional
    public Map<String, Object> generateMakeups(Integer semesterNo, String semesterLabel) {
        Set<String> exists = new HashSet<>();
        for (MakeupExam m : makeupRepo.findAll()) {
            if (!"CANCELLED".equals(m.getStatus())) exists.add(m.getStudentId() + ":" + m.getCourseId());
        }
        boolean byLabel = semesterLabel != null && !semesterLabel.isBlank();
        Map<Long, Integer> gradeCache = new HashMap<>();
        Map<String, GradeRecord> failed = new LinkedHashMap<>();
        for (GradeRecord r : recordRepo.findAll()) {
            if (!"normal".equals(r.getExamType())) continue;
            if (r.getTotalScore() == null || r.getTotalScore() >= GradeService.PASS_LINE) continue;
            if (byLabel) {
                if (!semesterLabel.equals(semesterLabelOf(gradeCache, r.getStudentId(), r.getSemesterNo()))) continue;
            } else if (semesterNo != null && !semesterNo.equals(r.getSemesterNo())) continue;
            failed.putIfAbsent(r.getStudentId() + ":" + r.getCourseId(), r);
        }
        int created = 0, skipped = 0;
        for (Map.Entry<String, GradeRecord> e : failed.entrySet()) {
            if (exists.contains(e.getKey())) { skipped++; continue; }
            GradeRecord r = e.getValue();
            MakeupExam m = new MakeupExam();
            m.setStudentId(r.getStudentId());
            m.setCourseId(r.getCourseId());
            m.setGradeRecordId(r.getId());
            m.setSemesterNo(r.getSemesterNo());
            m.setAttemptNo(1);
            m.setStatus("ELIGIBLE");
            makeupRepo.save(m);
            created++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("created", created);
        r.put("skipped", skipped);
        r.put("totalFailed", failed.size());
        return r;
    }

    @Transactional
    public Map<String, Object> scheduleMakeup(Long id, String examTime, String location, String invigilator) {
        MakeupExam m = makeupRepo.findById(id).orElseThrow(() -> new IllegalStateException("补考记录不存在"));
        if (GradeLedgerService.FINISHED.contains(m.getStatus()) || "ABSENT".equals(m.getStatus())) {
            throw new IllegalStateException("该补考已结束，不能再排考");
        }
        if (examTime != null && !examTime.isBlank()) {
            try { m.setExamTime(LocalDateTime.parse(examTime)); }
            catch (Exception ex) { throw new IllegalStateException("考试时间格式应为 yyyy-MM-ddTHH:mm"); }
        }
        if (location != null) m.setLocation(location);
        if (invigilator != null) m.setInvigilator(invigilator);
        if (m.getExamTime() != null && !"SCHEDULED".equals(m.getStatus())) {
            m.setStatus("SCHEDULED");
        }
        makeupRepo.save(m);
        return makeupRow(m);
    }

    @Transactional
    public Map<String, Object> absentMakeup(Long id) {
        return makeupRow(ledger.markAbsent(id));
    }

    /** 补考名册导出（.xlsx） */
    public byte[] exportMakeups(Integer semesterNo, String semesterLabel) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList("学号", "姓名", "学院", "年级", "课程", "学期",
                "补考次数", "状态", "成绩", "考试时间", "地点", "监考"));
        boolean byLabel = semesterLabel != null && !semesterLabel.isBlank();
        Map<Long, Integer> gradeCache = new HashMap<>();
        for (MakeupExam m : makeupRepo.findAll().stream()
                .sorted(Comparator.comparing(MakeupExam::getId)).toList()) {
            String label = semesterLabelOf(gradeCache, m.getStudentId(), m.getSemesterNo());
            if (byLabel && !semesterLabel.equals(label)) continue;
            if (!byLabel && semesterNo != null && !semesterNo.equals(m.getSemesterNo())) continue;
            User u = userRepo.findById(m.getStudentId()).orElse(null);
            Course c = courseRepo.findById(m.getCourseId()).orElse(null);
            rows.add(Arrays.asList(
                    u != null ? u.getNo() : "",
                    u != null ? u.getName() : "",
                    u != null ? u.getCollege() : "",
                    u != null && u.getGrade() != null ? u.getGrade() + "级" : "",
                    c != null ? c.getName() : "",
                    label,
                    String.valueOf(m.getAttemptNo() == null ? 1 : m.getAttemptNo()),
                    statusCn(m.getStatus()),
                    m.getScore() == null ? "" : String.valueOf(m.getScore()),
                    m.getExamTime() == null ? "" : m.getExamTime().toString().replace('T', ' '),
                    m.getLocation() == null ? "" : m.getLocation(),
                    m.getInvigilator() == null ? "" : m.getInvigilator()));
        }
        return excelService.toXlsx("补考名册", rows);
    }

    /* ==================== 重修 ==================== */

    public List<Map<String, Object>> myRetakes(Long studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RetakeApply r : retakeRepo.findByStudentIdOrderByIdDesc(studentId)) {
            if ("CANCELLED".equals(r.getStatus())) continue;
            Map<String, Object> row = retakeRow(r);
            row.put("canApply", "ELIGIBLE".equals(r.getStatus()));
            out.add(row);
        }
        return out;
    }

    /** 重修费用标准：200 元/学分（依据华厦教字〔2017〕8号） */
    private static final int RETAKE_FEE_PER_CREDIT = 200;

    /** 普通在校生只能重修「当学期开设」的课程；毕业班/结业生可申请所有学期的课程 */
    private boolean isGraduatingClass(User stu, int curSem) {
        if (stu == null || stu.getGrade() == null) return false;
        int totalSem = (stu.getMajor() != null && stu.getMajor().contains("专升本")) ? 4 : 8;
        return curSem >= totalSem - 1;
    }

    @Transactional
    public Map<String, Object> applyRetake(Long studentId, Long id) {
        RetakeApply r = retakeRepo.findById(id).orElseThrow(() -> new IllegalStateException("重修申请不存在"));
        if (!studentId.equals(r.getStudentId())) throw new IllegalStateException("无权操作他人的重修申请");
        if (!"ELIGIBLE".equals(r.getStatus())) throw new IllegalStateException("当前状态不可报名：" + statusCn(r.getStatus()));
        Course retakeCourse = courseRepo.findById(r.getCourseId())
                .orElseThrow(() -> new IllegalStateException("重修课程不存在"));
        User stu = userRepo.findById(studentId).orElse(null);
        int curSem = GraduationService.currentSemesterNo(
                stu == null ? null : stu.getGrade(), stu == null ? null : stu.getMajor(),
                java.time.LocalDate.now(), config().getSemesterOffset() == null ? 0 : config().getSemesterOffset());
        // 普通在校生只能重修「当学期开设」的课程；毕业班/结业生有特殊通道，可申请所有学期的课程
        if (!isGraduatingClass(stu, curSem) && retakeCourse.getSemesterNo() != null
                && retakeCourse.getSemesterNo() != curSem) {
            throw new IllegalStateException("普通在校生只能重修本学期开设的课程（"
                    + retakeCourse.getName() + " 开设于第 " + retakeCourse.getSemesterNo()
                    + " 学期）；毕业班/结业生可申请全部学期课程");
        }
        r.setStatus("APPLIED");
        r.setAppliedAt(LocalDateTime.now());
        retakeRepo.save(r);
        Map<String, Object> row = retakeRow(r);
        row.put("fee", RETAKE_FEE_PER_CREDIT * (retakeCourse.getCredit() == null ? 0 : retakeCourse.getCredit()));
        return row;
    }

    /** 跟班重修可选的教学班：同课程名、且排除与当前课表时间冲突之外的都返回（带冲突标记） */
    public List<Map<String, Object>> classOptions(Long studentId, Long courseId) {
        Course origin = courseRepo.findById(courseId).orElseThrow(() -> new IllegalStateException("课程不存在"));
        int round = currentRound();
        List<Enrollment> mine = enrollmentRepo.findByStudentId(studentId).stream()
                .filter(e -> "approved".equals(e.getStatus()) && (e.getRound() == null || e.getRound() == round))
                .toList();
        // 按课程记录已选时段：候选教学班的冲突判定要排除「本课程自己的现有报名」，
        // 否则重修选回同一门课会被自己判成冲突。
        Map<Long, List<String>> mySlotsByCourse = new HashMap<>();
        for (Enrollment e : mine) {
            courseRepo.findById(e.getCourseId())
                    .ifPresent(c -> mySlotsByCourse.computeIfAbsent(e.getCourseId(), k -> new ArrayList<>())
                            .addAll(slots(c.getTime())));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Course c : courseRepo.findAll()) {
            if (!Objects.equals(c.getName(), origin.getName())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("teacherName", teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("location", c.getLocation());
            m.put("campus", c.getCampus());
            m.put("credit", c.getCredit());
            m.put("capacity", c.getCapacity());
            m.put("enrolled", enrollmentRepo.findByCourseIdAndRound(c.getId(), round).stream()
                    .filter(e -> "approved".equals(e.getStatus())).count());
            List<String> cSlots = slots(c.getTime());
            List<String> otherSlots = new ArrayList<>();
            mySlotsByCourse.forEach((cid, sl) -> {
                if (!Objects.equals(cid, c.getId())) otherSlots.addAll(sl);
            });
            List<String> conflicts = new ArrayList<>();
            for (String s : cSlots) if (otherSlots.contains(s)) conflicts.add(s);
            m.put("slots", cSlots);
            m.put("conflicts", conflicts);
            m.put("conflict", !conflicts.isEmpty());
            out.add(m);
        }
        return out;
    }

    /**
     * 学生选定跟班教学班：重修册置 ENROLLED，并追加一条来源为 retake 的报名记录（0 积分），
     * 让课程出现在课表与「我的课程」里——这一步最容易漏，漏了学生会以为没选上。
     */
    @Transactional
    public Map<String, Object> chooseClass(Long studentId, Long id, Long targetCourseId) {
        RetakeApply r = retakeRepo.findById(id).orElseThrow(() -> new IllegalStateException("重修申请不存在"));
        if (!studentId.equals(r.getStudentId())) throw new IllegalStateException("无权操作他人的重修申请");
        if (!"APPLIED".equals(r.getStatus()) && !"APPROVED".equals(r.getStatus()) && !"ELIGIBLE".equals(r.getStatus())) {
            throw new IllegalStateException("当前状态不可选班：" + statusCn(r.getStatus()));
        }
        Course target = courseRepo.findById(targetCourseId)
                .orElseThrow(() -> new IllegalStateException("教学班不存在"));
        Course origin = courseRepo.findById(r.getCourseId()).orElse(null);
        if (origin != null && !Objects.equals(origin.getName(), target.getName())) {
            throw new IllegalStateException("只能选择同一门课程的教学班");
        }
        r.setForm("FOLLOW");
        r.setTargetClassId(targetCourseId);
        r.setStatus("ENROLLED");
        retakeRepo.save(r);

        int round = currentRound();
        User u = userRepo.findById(studentId).orElse(null);
        int semesterNo = GraduationService.currentSemesterNo(u != null ? u.getGrade() : null, u != null ? u.getMajor() : null, LocalDate.now(),
                config().getSemesterOffset() == null ? 0 : config().getSemesterOffset());
        Enrollment e = enrollmentRepo.findByStudentIdAndCourseIdAndRound(studentId, targetCourseId, round)
                .orElseGet(Enrollment::new);
        e.setStudentId(studentId);
        e.setCourseId(targetCourseId);
        e.setRound(round);
        e.setPoints(0);
        e.setStatus("approved");
        e.setSemesterNo(semesterNo);
        e.setSource("retake");
        enrollmentRepo.save(e);

        Map<String, Object> out = retakeRow(r);
        out.put("enrollmentId", e.getId());
        return out;
    }

    public List<Map<String, Object>> listRetakes(String status, String form, Long courseId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RetakeApply r : retakeRepo.findAll().stream()
                .sorted(Comparator.comparing(RetakeApply::getId).reversed()).toList()) {
            if (status != null && !status.isBlank() && !status.equals(r.getStatus())) continue;
            if (form != null && !form.isBlank() && !form.equals(r.getForm())) continue;
            if (courseId != null && !courseId.equals(r.getCourseId())) continue;
            out.add(retakeRow(r));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> setRetakeForm(Long id, String form, Long targetClassId) {
        RetakeApply r = retakeRepo.findById(id).orElseThrow(() -> new IllegalStateException("重修申请不存在"));
        String f = form == null ? "" : form.toUpperCase(Locale.ROOT);
        if (!List.of("GROUP", "FOLLOW", "SELF").contains(f)) {
            throw new IllegalStateException("重修形式只能是 GROUP / FOLLOW / SELF");
        }
        if ("FOLLOW".equals(f)) {
            if (targetClassId == null) throw new IllegalStateException("跟班重修必须指定教学班");
            Course target = courseRepo.findById(targetClassId).orElseThrow(() -> new IllegalStateException("教学班不存在"));
            Course origin = courseRepo.findById(r.getCourseId()).orElse(null);
            if (origin != null && !Objects.equals(origin.getName(), target.getName())) {
                throw new IllegalStateException("只能指定同一门课程的教学班");
            }
            r.setTargetClassId(targetClassId);
        } else {
            r.setTargetClassId(null);
        }
        r.setForm(f);
        if ("ELIGIBLE".equals(r.getStatus()) || "APPLIED".equals(r.getStatus())) r.setStatus("APPROVED");
        retakeRepo.save(r);
        return retakeRow(r);
    }

    /** 报名汇总：课程 × 形式 */
    public List<Map<String, Object>> retakeSummary() {
        Map<Long, Map<String, Object>> byCourse = new LinkedHashMap<>();
        int threshold = cfgInt(config().getRetakeGroupThreshold(), 30);   // 华厦规则：单门课报名 ≥30 人单独开班
        for (RetakeApply r : retakeRepo.findAll()) {
            if ("CANCELLED".equals(r.getStatus())) continue;
            Course c = courseRepo.findById(r.getCourseId()).orElse(null);
            Map<String, Object> row = byCourse.computeIfAbsent(r.getCourseId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("courseId", k);
                m.put("courseName", c != null ? c.getName() : "（课程已删除）");
                m.put("total", 0);
                m.put("eligible", 0);
                m.put("applied", 0);
                m.put("group", 0);
                m.put("follow", 0);
                m.put("self", 0);
                m.put("unset", 0);
                return m;
            });
            row.put("total", (int) row.get("total") + 1);
            if ("ELIGIBLE".equals(r.getStatus())) row.put("eligible", (int) row.get("eligible") + 1);
            if ("APPLIED".equals(r.getStatus()) || "APPROVED".equals(r.getStatus())
                    || "ENROLLED".equals(r.getStatus())) row.put("applied", (int) row.get("applied") + 1);
            String f = r.getForm();
            if (f == null) row.put("unset", (int) row.get("unset") + 1);
            else switch (f) {
                case "GROUP" -> row.put("group", (int) row.get("group") + 1);
                case "FOLLOW" -> row.put("follow", (int) row.get("follow") + 1);
                case "SELF" -> row.put("self", (int) row.get("self") + 1);
                default -> row.put("unset", (int) row.get("unset") + 1);
            }
            row.put("groupThreshold", threshold);
            row.put("canGroup", (int) row.get("group") >= threshold);
        }
        return new ArrayList<>(byCourse.values());
    }

    /* ==================== 统计 ==================== */

    public Map<String, Object> stats() {
        long graded = 0, failed = 0;
        for (Grade g : gradeRepo.findAll()) {
            if (g.getTotalScore() == null) continue;
            graded++;
            if (g.getTotalScore() < GradeService.PASS_LINE) failed++;
        }
        long makeupPassed = 0, makeupFailed = 0, makeupAbsent = 0, makeupPending = 0, retakeOpen = 0, retakePassed = 0;
        for (MakeupExam m : makeupRepo.findAll()) {
            switch (m.getStatus()) {
                case "PASSED" -> makeupPassed++;
                case "FAILED" -> makeupFailed++;
                case "ABSENT" -> makeupAbsent++;
                case "ELIGIBLE", "REGISTERED", "SCHEDULED" -> makeupPending++;
                default -> { }
            }
        }
        long retakeTotal = 0;
        Set<Long> retakeStudents = new HashSet<>();
        for (RetakeApply r : retakeRepo.findAll()) {
            if ("CANCELLED".equals(r.getStatus())) continue;
            retakeTotal++;
            retakeStudents.add(r.getStudentId());
            if ("PASSED".equals(r.getStatus())) retakePassed++;
            else if (!"FAILED".equals(r.getStatus())) retakeOpen++;
        }
        // 重修率分母用「曾经正考挂科的学生数」（流水口径），分子用有重修记录的学生数，
        // 避免按人次相除出现 >100% 的失真值。
        Set<Long> everFailedStudents = new HashSet<>();
        for (GradeRecord r : recordRepo.findAll()) {
            if ("normal".equals(r.getExamType()) && r.getTotalScore() != null
                    && r.getTotalScore() < GradeService.PASS_LINE) {
                everFailedStudents.add(r.getStudentId());
            }
        }
        long makeupFinished = makeupPassed + makeupFailed + makeupAbsent;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gradedCount", graded);
        m.put("failCount", failed);
        m.put("failRate", graded > 0 ? Math.round(failed * 10000.0 / graded) / 100.0 : 0);
        m.put("makeupTotal", makeupPassed + makeupFailed + makeupAbsent + makeupPending);
        m.put("makeupPending", makeupPending);
        m.put("makeupPassed", makeupPassed);
        m.put("makeupFailed", makeupFailed);
        m.put("makeupAbsent", makeupAbsent);
        m.put("makeupFinished", makeupFinished);
        m.put("makeupPassRate", makeupFinished > 0 ? Math.round(makeupPassed * 10000.0 / makeupFinished) / 100.0 : 0);
        m.put("retakeTotal", retakeTotal);
        m.put("retakeStudents", retakeStudents.size());
        m.put("everFailedStudents", everFailedStudents.size());
        m.put("retakeOpen", retakeOpen);
        m.put("retakePassed", retakePassed);
        m.put("retakeRate", everFailedStudents.isEmpty() ? 0
                : Math.min(100, Math.round(retakeStudents.size() * 10000.0 / everFailedStudents.size()) / 100.0));
        return m;
    }

    /* ==================== 内部工具 ==================== */

    private Map<String, Object> makeupRow(MakeupExam m) {
        User u = userRepo.findById(m.getStudentId()).orElse(null);
        Course c = courseRepo.findById(m.getCourseId()).orElse(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("studentId", m.getStudentId());
        row.put("studentNo", u != null ? u.getNo() : "");
        row.put("studentName", u != null ? u.getName() : "未知");
        row.put("studentClass", u != null ? u.getClassNo() : "");
        row.put("college", u != null ? u.getCollege() : "");
        row.put("major", u != null ? u.getMajor() : "");
        row.put("grade", u != null ? u.getGrade() : null);
        row.put("courseId", m.getCourseId());
        row.put("courseName", c != null ? c.getName() : "（课程已删除）");
        row.put("credit", c != null ? c.getCredit() : null);
        row.put("teacherName", c != null ? teacherName(c.getTeacherId()) : "");
        row.put("semesterNo", m.getSemesterNo());
        row.put("semesterLabel", semesterLabelOf(new HashMap<>(), m.getStudentId(), m.getSemesterNo()));
        row.put("attemptNo", m.getAttemptNo());
        row.put("status", m.getStatus());
        row.put("statusText", statusCn(m.getStatus()));
        row.put("score", m.getScore());
        row.put("examTime", m.getExamTime());
        row.put("location", m.getLocation());
        row.put("invigilator", m.getInvigilator());
        row.put("registeredAt", m.getRegisteredAt());
        row.put("finishedAt", m.getFinishedAt());
        return row;
    }

    private Map<String, Object> retakeRow(RetakeApply r) {
        User u = userRepo.findById(r.getStudentId()).orElse(null);
        Course c = courseRepo.findById(r.getCourseId()).orElse(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("studentId", r.getStudentId());
        row.put("studentNo", u != null ? u.getNo() : "");
        row.put("studentName", u != null ? u.getName() : "未知");
        row.put("studentClass", u != null ? u.getClassNo() : "");
        row.put("college", u != null ? u.getCollege() : "");
        row.put("major", u != null ? u.getMajor() : "");
        row.put("grade", u != null ? u.getGrade() : null);
        row.put("courseId", r.getCourseId());
        row.put("courseName", c != null ? c.getName() : "（课程已删除）");
        row.put("credit", c != null ? c.getCredit() : null);
        row.put("semesterNo", r.getSemesterNo());
        row.put("semesterLabel", semesterLabelOf(new HashMap<>(), r.getStudentId(), r.getSemesterNo()));
        row.put("attemptNo", r.getAttemptNo());
        row.put("status", r.getStatus());
        row.put("statusText", statusCn(r.getStatus()));
        row.put("form", r.getForm());
        row.put("formText", formCn(r.getForm()));
        row.put("targetClassId", r.getTargetClassId());
        row.put("targetClassName", r.getTargetClassId() == null ? null
                : courseRepo.findById(r.getTargetClassId()).map(Course::getName).orElse(null));
        TeachingClass tc = r.getTeachingClassId() == null ? null
                : teachingClassRepo.findById(r.getTeachingClassId()).orElse(null);
        row.put("teachingClassId", r.getTeachingClassId());
        row.put("teachingClassNo", tc != null ? tc.getClassNo() : null);
        row.put("teachingClassTime", tc != null ? tc.getTime() : null);
        row.put("teachingClassLocation", tc != null ? tc.getLocation() : null);
        row.put("teachingClassTeacher", tc != null && tc.getTeacherId() != null
                ? teacherName(tc.getTeacherId()) : null);
        // 重修费用：200 元/学分（依据华厦教字〔2017〕8号），按课程学分计算
        row.put("fee", RETAKE_FEE_PER_CREDIT * (c != null && c.getCredit() != null ? c.getCredit() : 0));
        row.put("score", r.getScore());
        row.put("appliedAt", r.getAppliedAt());
        row.put("finishedAt", r.getFinishedAt());
        return row;
    }

    private boolean windowOpen(SystemConfig cfg) {
        LocalDateTime now = LocalDateTime.now();
        if (cfg.getMakeupOpenAt() != null && now.isBefore(cfg.getMakeupOpenAt())) return false;
        if (cfg.getMakeupCloseAt() != null && now.isAfter(cfg.getMakeupCloseAt())) return false;
        return true;
    }

    private boolean canRegister(MakeupExam m, SystemConfig cfg) {
        return "ELIGIBLE".equals(m.getStatus()) && windowOpen(cfg);
    }

    /** "周一 08:00-09:40 / 周三 10:00-11:40" → ["周一08:00-09:40", ...] */
    private static List<String> slots(String time) {
        List<String> out = new ArrayList<>();
        if (time == null || time.isBlank()) return out;
        for (String part : time.split("/")) {
            String s = part.trim().replaceAll("\\s+", "");
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private int currentRound() {
        Integer r = enrollmentService.getSystemConfig().getCurrentRound();
        return r == null ? 1 : r;
    }

    private SystemConfig config() {
        return sysRepo.findById(1L).orElseGet(SystemConfig::new);
    }

    private static int cfgInt(Integer v, int def) { return v == null ? def : v; }

    private String teacherName(Long teacherId) {
        if (teacherId == null) return "";
        return userRepo.findById(teacherId).map(User::getName).orElse("");
    }

    /** 学期序号 → 「学期设置」里的学期名（按学生年级换算），供列表与名册展示/筛选 */
    private String semesterLabelOf(Map<Long, Integer> gradeCache, Long studentId, Integer semesterNo) {
        if (semesterNo == null) return "";
        Integer grade = gradeCache.computeIfAbsent(studentId, id -> {
            User u = userRepo.findById(id).orElse(null);
            return u == null ? null : u.getGrade();
        });
        return GraduationService.semesterLabel(grade, semesterNo);
    }

    public static String statusCn(String s) {
        if (s == null) return "";
        return switch (s) {
            case "ELIGIBLE" -> "待报名";
            case "REGISTERED" -> "已报名";
            case "SCHEDULED" -> "已排考";
            case "APPLIED" -> "已报名";
            case "APPROVED" -> "已同意";
            case "ENROLLED" -> "已进班";
            case "PASSED" -> "已通过";
            case "FAILED" -> "未通过";
            case "ABSENT" -> "缺考";
            case "CANCELLED" -> "已取消";
            default -> s;
        };
    }

    public static String formCn(String f) {
        if (f == null) return "未定";
        return switch (f) {
            case "GROUP" -> "组班";
            case "FOLLOW" -> "跟班";
            case "SELF" -> "自主学习（学习通）";
            default -> f;
        };
    }
}
