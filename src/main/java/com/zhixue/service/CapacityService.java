package com.zhixue.service;

import com.zhixue.entity.Enrollment;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.DropRequestRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容量释放。
 *
 * 业务口径（startNewCycle）：**不删除任何报名数据**。推进学期偏移后，旧记录的
 * semesterNo 自动成为历史学期；容量统计、结算座位池、第三轮校验、积分池全部按
 * 「学生当前学期」过滤，新学期名额自然满额，历年选课记录与成绩完整保留。
 * 候补 / 待处理退课申请只改状态为 expired（归档，保留行），不删除。
 *
 * releaseAll() 为早期「清空本学期数据」的实现，仅保留作演示数据重置，
 * 开第 1 轮与手动释放容量都不再调用它。
 */
@Service
public class CapacityService {

    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private DropRequestRepository dropRequestRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository sysRepo;

    /**
     * 开启新学期：推进学期 → 名额按学期口径释放，数据一条不删；
     * 同时把 newSemesterReady 置 true，解锁第 1 轮选课。
     * 幂等：已开启时不再重复推进学期，避免两个入口（学期管理 / 轮次页）把学期跳格。
     */
    @Transactional
    public Map<String, Object> startNewCycle() {
        SystemConfig sys = sysRepo.findById(1L).orElse(null);
        int off = sys == null || sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset();
        Map<String, Object> m = new LinkedHashMap<>();
        if (sys != null && Boolean.TRUE.equals(sys.getNewSemesterReady())) {
            m.put("alreadyReady", true);
            m.put("semesterOffset", off);
            m.put("keptEnrollments", enrollmentRepo.count());
            m.put("archivedWaitlists", 0);
            m.put("archivedDropRequests", 0);
            return m;
        }
        int newOff = off + 1;
        if (sys != null) {
            sys.setSemesterOffset(newOff);
            sys.setCurrentRound(1);
            sys.setRoundProgress(0);       // 新学期从 0 开始，只解锁第 1 轮
            sys.setRoundOpen(false);
            sys.setAutoSchedule(false);
            sys.setNewSemesterReady(true);
            sysRepo.save(sys);
        }
        int archivedWaits = waitlistRepo.archiveActive();
        int archivedDrops = dropRequestRepo.archivePending();

        m.put("alreadyReady", false);
        m.put("semesterOffset", newOff);
        m.put("keptEnrollments", enrollmentRepo.count());   // 一条都没删
        m.put("archivedWaitlists", archivedWaits);
        m.put("archivedDropRequests", archivedDrops);
        m.put("freedSeats", "按学期口径释放，未删除记录");
        return m;
    }

    /** 仅用于演示数据重置：清空本学期报名 / 候补 / 待处理退课申请（业务链路已不再调用） */
    @Transactional
    public Map<String, Object> releaseAll() {
        SystemConfig sys = sysRepo.findById(1L).orElse(null);
        int offset = sys == null || sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset();

        // 每个学生当前学期序（不同年级不同，必须逐个算，不能用全局值）
        Map<Long, Integer> curSemByStudent = new HashMap<>();
        for (User u : userRepo.findByRole("student")) {
            curSemByStudent.put(u.getId(),
                    GraduationService.currentSemesterNo(u.getGrade(), u.getMajor(), LocalDate.now(), offset));
        }

        List<Enrollment> all = enrollmentRepo.findAll();
        List<Enrollment> toClear = new ArrayList<>();
        long keptHistory = 0, cleared = 0, freedSeats = 0;
        for (Enrollment e : all) {
            int cur = curSemByStudent.getOrDefault(e.getStudentId(), 1);
            Integer sem = e.getSemesterNo();
            if (sem != null && sem < cur) {          // 历史学期：保留
                keptHistory++;
                continue;
            }
            toClear.add(e);
            cleared++;
            if ("approved".equals(e.getStatus())) freedSeats++;
        }

        long waitlists = waitlistRepo.count();
        long pendingDrops = dropRequestRepo.countByStatus("pending");

        // 分批删除：一次拼几千个 id 会让 Hibernate 的 HQL 解析器递归过深（实测 StackOverflowError）
        for (int i = 0; i < toClear.size(); i += 300) {
            enrollmentRepo.deleteAllInBatch(toClear.subList(i, Math.min(i + 300, toClear.size())));
        }
        waitlistRepo.deleteAllInBatch();
        dropRequestRepo.deleteByStatus("pending");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enrollments", cleared);          // 本学期清掉的报名条数
        m.put("approved", freedSeats);          // 其中已录取、真正占座的条数
        m.put("keptHistory", keptHistory);      // 历史学期保留的条数
        m.put("waitlists", waitlists);
        m.put("dropRequests", pendingDrops);
        m.put("freedSeats", freedSeats);
        return m;
    }
}
