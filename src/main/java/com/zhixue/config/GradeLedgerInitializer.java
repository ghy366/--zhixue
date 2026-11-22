package com.zhixue.config;

import com.zhixue.entity.Grade;
import com.zhixue.entity.GradeRecord;
import com.zhixue.entity.MakeupExam;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.GradeRecordRepository;
import com.zhixue.repository.GradeRepository;
import com.zhixue.repository.MakeupExamRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.service.GradeLedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成绩流水迁移（幂等，可重复启动）：
 * 1. 清洗历史脏值：exam_type 的 NULL / 中文「正常考试」等统一为 normal/makeup/retake，attempt 补 1；
 * 2. 把现有 grades 每一行复制成 attempt=当前值的 grade_records（历史无法还原，只从当前快照开始记流水）；
 * 3. T1 补历史：正考挂科但还没有补考册的，补生成一条 ELIGIBLE。
 */
@Component
public class GradeLedgerInitializer {

    private static final Logger log = LoggerFactory.getLogger(GradeLedgerInitializer.class);

    @Autowired private GradeRepository gradeRepo;
    @Autowired private GradeRecordRepository recordRepo;
    @Autowired private MakeupExamRepository makeupRepo;
    @Autowired private SystemConfigRepository sysRepo;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        fillConfigDefaults();
        List<Grade> all = gradeRepo.findAll();
        int cleaned = 0;
        int copied = 0;
        int makeupCreated = 0;

        for (Grade g : all) {
            boolean dirty = false;
            String type = GradeLedgerService.normalizeType(g.getExamType());
            if (!type.equals(g.getExamType())) {
                g.setExamType(type);
                dirty = true;
            }
            if (g.getAttempt() == null || g.getAttempt() < 1) {
                g.setAttempt(1);
                dirty = true;
            }
            if (dirty) {
                gradeRepo.save(g);
                cleaned++;
            }
            if (!recordRepo.existsByStudentIdAndCourseId(g.getStudentId(), g.getCourseId())) {
                recordRepo.save(toRecord(g));
                copied++;
            }
        }

        for (Grade g : all) {
            if (!"normal".equals(g.getExamType())
                    || g.getTotalScore() == null
                    || g.getTotalScore() >= 60) continue;
            List<MakeupExam> exists = makeupRepo
                    .findByStudentIdAndCourseIdOrderByIdAsc(g.getStudentId(), g.getCourseId());
            if (!exists.isEmpty()) continue;
            MakeupExam m = new MakeupExam();
            m.setStudentId(g.getStudentId());
            m.setCourseId(g.getCourseId());
            m.setSemesterNo(g.getSemesterNo());
            m.setAttemptNo(1);
            m.setStatus("ELIGIBLE");
            m.setGradeRecordId(recordRepo
                    .findFirstByStudentIdAndCourseIdOrderByAttemptNoDesc(g.getStudentId(), g.getCourseId())
                    .map(GradeRecord::getId).orElse(null));
            makeupRepo.save(m);
            makeupCreated++;
        }

        if (cleaned + copied + makeupCreated > 0) {
            log.info("成绩流水迁移完成：清洗 {} 条，复制流水 {} 条，补建补考册 {} 条", cleaned, copied, makeupCreated);
        }
    }

    private void fillConfigDefaults() {
        sysRepo.findById(1L).ifPresent(c -> {
            boolean dirty = false;
            if (c.getMakeupScoreCap() == null) { c.setMakeupScoreCap(60); dirty = true; }
            if (c.getMakeupMaxAttempts() == null) { c.setMakeupMaxAttempts(1); dirty = true; }
            if (c.getMaxRetakeAttempts() == null) { c.setMaxRetakeAttempts(2); dirty = true; }
            if (c.getRetakeGroupThreshold() == null) { c.setRetakeGroupThreshold(15); dirty = true; }
            if (dirty) sysRepo.save(c);
        });
    }

    private GradeRecord toRecord(Grade g) {
        GradeRecord r = new GradeRecord();
        r.setStudentId(g.getStudentId());
        r.setCourseId(g.getCourseId());
        r.setAttemptNo(g.getAttempt() == null ? 1 : g.getAttempt());
        r.setExamType(GradeLedgerService.normalizeType(g.getExamType()));
        r.setRegularScore(g.getRegularScore());
        r.setFinalScore(g.getFinalScore());
        r.setTotalScore(g.getTotalScore());
        r.setSemesterNo(g.getSemesterNo());
        r.setRecordedAt(LocalDateTime.now());
        return r;
    }
}
