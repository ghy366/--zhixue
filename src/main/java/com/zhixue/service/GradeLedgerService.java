package com.zhixue.service;

import com.zhixue.entity.Grade;
import com.zhixue.entity.GradeRecord;
import com.zhixue.entity.MakeupExam;
import com.zhixue.entity.RetakeApply;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.GradeRecordRepository;
import com.zhixue.repository.MakeupExamRepository;
import com.zhixue.repository.RetakeApplyRepository;
import com.zhixue.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 成绩流水与补考/重修状态机。
 * <p>
 * T1 正考 &lt; 60 → 生成补考册（ELIGIBLE）
 * T2 补考录分 → PASSED / FAILED（分数按配置封顶）
 * T3 补考 FAILED/ABSENT → 生成重修申请（ELIGIBLE），受重修次数上限约束
 * T4 重修录分 → PASSED / FAILED；未过且未达上限则再生成一条 ELIGIBLE
 * <p>
 * 兼容策略：grades 表继续作为「最新一次成绩」视图用于录入与成绩单展示；
 * GPA、专业排名、毕业学分与通过判定统一读取 grade_records 的有效记录（最新一次通过优先）。
 */
@Service
public class GradeLedgerService {

    public static final List<String> PENDING_MAKEUP = List.of("ELIGIBLE", "REGISTERED", "SCHEDULED");
    public static final List<String> OPEN_RETAKE = List.of("ELIGIBLE", "APPLIED", "APPROVED", "ENROLLED");
    public static final List<String> FINISHED = List.of("PASSED", "FAILED");

    @Autowired private GradeRecordRepository recordRepo;
    @Autowired private MakeupExamRepository makeupRepo;
    @Autowired private RetakeApplyRepository retakeRepo;
    @Autowired private SystemConfigRepository sysRepo;

    // ---------- 查询 ----------

    public List<GradeRecord> history(Long studentId, Long courseId) {
        return recordRepo.findByStudentIdAndCourseIdOrderByAttemptNoAsc(studentId, courseId);
    }

    public Optional<GradeRecord> latest(Long studentId, Long courseId) {
        return recordRepo.findFirstByStudentIdAndCourseIdOrderByAttemptNoDesc(studentId, courseId);
    }

    /** 是否曾经通过（先修课、毕业判定用它，避免被后续失败记录覆盖） */
    public boolean hasPassed(Long studentId, Long courseId) {
        return recordRepo.existsByStudentIdAndCourseIdAndTotalScoreGreaterThanEqual(
                studentId, courseId, GradeService.PASS_LINE);
    }

    public int attemptCount(Long studentId, Long courseId) {
        return (int) recordRepo.countByStudentIdAndCourseId(studentId, courseId);
    }

    /**
     * 有效成绩记录（GPA / 毕业学分 / 排名的唯一口径）：
     * 优先取「最新一次通过」（通过记录不被后续失败覆盖），无通过记录时取最新一次修读。
     */
    public Optional<GradeRecord> effectiveRecord(Long studentId, Long courseId) {
        GradeRecord best = null;
        for (GradeRecord r : history(studentId, courseId)) {
            if (r.getTotalScore() == null) continue;
            if (best == null || betterThan(r, best)) best = r;
        }
        return Optional.ofNullable(best);
    }

    /** 单学生全部课程的有效成绩：courseId → effective record（批量版，口径同 effectiveRecord） */
    public Map<Long, GradeRecord> effectiveRecords(Long studentId) {
        Map<Long, GradeRecord> out = new LinkedHashMap<>();
        for (GradeRecord r : recordRepo.findByStudentIdOrderByCourseIdAscAttemptNoAsc(studentId)) {
            if (r.getTotalScore() == null) continue;
            GradeRecord cur = out.get(r.getCourseId());
            if (cur == null || betterThan(r, cur)) out.put(r.getCourseId(), r);
        }
        return out;
    }

    /** 全校有效成绩：studentId → (courseId → effective record)，用于专业排名批量聚合 */
    public Map<Long, Map<Long, GradeRecord>> effectiveRecordsAll() {
        Map<Long, Map<Long, GradeRecord>> out = new HashMap<>();
        for (GradeRecord r : recordRepo.findAll()) {
            if (r.getTotalScore() == null) continue;
            Map<Long, GradeRecord> byCourse = out.computeIfAbsent(r.getStudentId(), k -> new HashMap<>());
            GradeRecord cur = byCourse.get(r.getCourseId());
            if (cur == null || betterThan(r, cur)) byCourse.put(r.getCourseId(), r);
        }
        return out;
    }

    /** 有效成绩的绩点：补考通过一律封顶 2.0（不刷绩点），正考/重修按实际分数 */
    public static double pointsOf(GradeRecord r) {
        if (r == null || r.getTotalScore() == null) return 0;
        double pts = GradeService.scoreToGpa(r.getTotalScore());
        return "makeup".equals(normalizeType(r.getExamType())) ? Math.min(2.0, pts) : pts;
    }

    /** 记录优劣：通过优先于未通过；同通过状态取 attempt_no 更大（更晚）的一条 */
    private static boolean betterThan(GradeRecord candidate, GradeRecord current) {
        boolean candidatePass = candidate.getTotalScore() >= GradeService.PASS_LINE;
        boolean currentPass = current.getTotalScore() >= GradeService.PASS_LINE;
        if (candidatePass != currentPass) return candidatePass;
        int ca = candidate.getAttemptNo() == null ? 0 : candidate.getAttemptNo();
        int cb = current.getAttemptNo() == null ? 0 : current.getAttemptNo();
        if (ca != cb) return ca > cb;
        long ia = candidate.getId() == null ? 0 : candidate.getId();
        long ib = current.getId() == null ? 0 : current.getId();
        return ia > ib;
    }

    public Optional<MakeupExam> activeMakeup(Long studentId, Long courseId) {
        return makeupRepo.findFirstByStudentIdAndCourseIdOrderByIdDesc(studentId, courseId)
                .filter(m -> !"CANCELLED".equals(m.getStatus()));
    }

    public Optional<RetakeApply> activeRetake(Long studentId, Long courseId) {
        return retakeRepo.findFirstByStudentIdAndCourseIdOrderByIdDesc(studentId, courseId)
                .filter(r -> !"CANCELLED".equals(r.getStatus()));
    }

    // ---------- 写入 ----------

    /** 补考成绩封顶（配置 0/null 表示不封顶） */
    public double capScore(String examType, double total) {
        if (!"makeup".equals(examType)) return total;
        int cap = intCfg(config().getMakeupScoreCap(), 60);
        return cap > 0 ? Math.min(total, cap) : total;
    }

    /**
     * 成绩保存后调用：写流水 + 推进状态机。与 {@link GradeService#save} 同事务。
     */
    @Transactional
    public void afterGradeSaved(Grade g) {
        String type = normalizeType(g.getExamType());
        int attemptNo = g.getAttempt() == null ? 1 : Math.max(1, g.getAttempt());

        GradeRecord rec = recordRepo
                .findByStudentIdAndCourseIdAndAttemptNo(g.getStudentId(), g.getCourseId(), attemptNo)
                .orElseGet(GradeRecord::new);
        boolean fresh = rec.getId() == null;
        rec.setStudentId(g.getStudentId());
        rec.setCourseId(g.getCourseId());
        rec.setAttemptNo(attemptNo);
        rec.setExamType(type);
        rec.setRegularScore(g.getRegularScore());
        rec.setFinalScore(g.getFinalScore());
        rec.setTotalScore(g.getTotalScore());
        rec.setSemesterNo(g.getSemesterNo());
        if (fresh) rec.setRecordedAt(LocalDateTime.now());
        rec = recordRepo.save(rec);

        double total = g.getTotalScore() == null ? 0 : g.getTotalScore();
        switch (type) {
            case "makeup" -> settleMakeup(g, rec, total);
            case "retake" -> settleRetake(g, rec, total);
            default -> {
                if (total < GradeService.PASS_LINE) createMakeupIfAbsent(g, rec);
                else cancelOpenFlows(g.getStudentId(), g.getCourseId());
            }
        }
    }

    private void settleMakeup(Grade g, GradeRecord rec, double total) {
        MakeupExam m = makeupRepo
                .findFirstByStudentIdAndCourseIdOrderByIdDesc(g.getStudentId(), g.getCourseId())
                .orElseGet(MakeupExam::new);
        if (m.getId() == null) {
            m.setStudentId(g.getStudentId());
            m.setCourseId(g.getCourseId());
            m.setAttemptNo((int) makeupRepo.countByStudentIdAndCourseId(g.getStudentId(), g.getCourseId()) + 1);
            m.setSemesterNo(g.getSemesterNo());
            m.setGradeRecordId(findNormalRecordId(g));
        }
        m.setScore(total);
        m.setStatus(total >= GradeService.PASS_LINE ? "PASSED" : "FAILED");
        m.setFinishedAt(LocalDateTime.now());
        m.setGradeRecordId(rec.getId());
        m = makeupRepo.save(m);
        if (total < GradeService.PASS_LINE) {
            createRetakeIfAllowed(g, rec.getId(), m.getId());
        }
    }

    private void settleRetake(Grade g, GradeRecord rec, double total) {
        RetakeApply r = retakeRepo
                .findFirstByStudentIdAndCourseIdOrderByIdDesc(g.getStudentId(), g.getCourseId())
                .orElseGet(RetakeApply::new);
        if (r.getId() == null) {
            r.setStudentId(g.getStudentId());
            r.setCourseId(g.getCourseId());
            r.setAttemptNo((int) retakeRepo.countByStudentIdAndCourseId(g.getStudentId(), g.getCourseId()) + 1);
            r.setSemesterNo(g.getSemesterNo());
            r.setGradeRecordId(rec.getId());
        }
        r.setScore(total);
        r.setStatus(total >= GradeService.PASS_LINE ? "PASSED" : "FAILED");
        r.setFinishedAt(LocalDateTime.now());
        r.setGradeRecordId(rec.getId());
        r = retakeRepo.save(r);
        if (total < GradeService.PASS_LINE) {
            createRetakeIfAllowed(g, rec.getId(), r.getMakeupExamId());
        }
    }

    private void createMakeupIfAbsent(Grade g, GradeRecord rec) {
        List<MakeupExam> all = makeupRepo.findByStudentIdAndCourseIdOrderByIdAsc(g.getStudentId(), g.getCourseId());
        boolean hasPassed = all.stream().anyMatch(m -> "PASSED".equals(m.getStatus()));
        boolean hasOpen = all.stream().anyMatch(m -> PENDING_MAKEUP.contains(m.getStatus()));
        if (hasPassed || hasOpen) return;
        int max = intCfg(config().getMakeupMaxAttempts(), 1);
        long finished = all.stream().filter(m -> FINISHED.contains(m.getStatus())).count();
        if (finished >= max) return;

        MakeupExam m = new MakeupExam();
        m.setStudentId(g.getStudentId());
        m.setCourseId(g.getCourseId());
        m.setGradeRecordId(rec.getId());
        m.setSemesterNo(g.getSemesterNo());
        m.setAttemptNo((int) finished + 1);
        m.setStatus("ELIGIBLE");
        makeupRepo.save(m);
    }

    private void createRetakeIfAllowed(Grade g, Long gradeRecordId, Long makeupExamId) {
        createRetakeIfAllowed(g.getStudentId(), g.getCourseId(), g.getSemesterNo(), gradeRecordId, makeupExamId);
    }

    /** T3：补考未通过 / 弃考后生成重修申请，受重修次数上限约束（已有进行中的申请则跳过） */
    private void createRetakeIfAllowed(Long studentId, Long courseId, Integer semesterNo,
                                       Long gradeRecordId, Long makeupExamId) {
        int max = intCfg(config().getMaxRetakeAttempts(), 2);
        long finished = retakeRepo.countByStudentIdAndCourseIdAndStatusIn(studentId, courseId, FINISHED);
        boolean hasOpen = retakeRepo.findByStudentIdAndCourseIdOrderByIdAsc(studentId, courseId)
                .stream().anyMatch(r -> OPEN_RETAKE.contains(r.getStatus()));
        if (hasOpen || finished >= max) return;

        RetakeApply r = new RetakeApply();
        r.setStudentId(studentId);
        r.setCourseId(courseId);
        r.setMakeupExamId(makeupExamId);
        r.setGradeRecordId(gradeRecordId);
        r.setAttemptNo((int) finished + 1);
        r.setSemesterNo(semesterNo);
        r.setStatus("ELIGIBLE");
        retakeRepo.save(r);
    }

    /**
     * 教务标记弃考：补考册置 ABSENT，并立即推进 T3（生成重修申请）。
     * 只有未完成的补考才允许标记，避免覆盖已通过的记录。
     */
    @Transactional
    public MakeupExam markAbsent(Long makeupExamId) {
        MakeupExam m = makeupRepo.findById(makeupExamId)
                .orElseThrow(() -> new IllegalStateException("补考记录不存在"));
        if (!PENDING_MAKEUP.contains(m.getStatus())) {
            throw new IllegalStateException("该补考已结束（" + m.getStatus() + "），不能标记弃考");
        }
        m.setStatus("ABSENT");
        m.setFinishedAt(LocalDateTime.now());
        m = makeupRepo.save(m);
        createRetakeIfAllowed(m.getStudentId(), m.getCourseId(), m.getSemesterNo(),
                m.getGradeRecordId(), m.getId());
        return m;
    }

    /** 正考改回及格时，撤销尚未完成的补考/重修 */
    private void cancelOpenFlows(Long studentId, Long courseId) {
        LocalDateTime now = LocalDateTime.now();
        for (MakeupExam m : makeupRepo.findByStudentIdAndCourseIdOrderByIdAsc(studentId, courseId)) {
            if (PENDING_MAKEUP.contains(m.getStatus())) {
                m.setStatus("CANCELLED");
                m.setFinishedAt(now);
                makeupRepo.save(m);
            }
        }
        for (RetakeApply r : retakeRepo.findByStudentIdAndCourseIdOrderByIdAsc(studentId, courseId)) {
            if (OPEN_RETAKE.contains(r.getStatus())) {
                r.setStatus("CANCELLED");
                r.setFinishedAt(now);
                retakeRepo.save(r);
            }
        }
    }

    private Long findNormalRecordId(Grade g) {
        return recordRepo.findByStudentIdAndCourseIdOrderByAttemptNoAsc(g.getStudentId(), g.getCourseId())
                .stream().filter(r -> "normal".equals(r.getExamType())).findFirst()
                .map(GradeRecord::getId).orElse(null);
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "normal";
        return switch (type.trim()) {
            case "makeup", "补考" -> "makeup";
            case "retake", "重修" -> "retake";
            default -> "normal";
        };
    }

    private SystemConfig config() {
        return sysRepo.findById(1L).orElseGet(SystemConfig::new);
    }

    private static int intCfg(Integer v, int def) {
        return v == null ? def : v;
    }
}
