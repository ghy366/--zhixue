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
 * 容量释放：新学期第 1 轮开启前，把「本学期」占用的名额清空，
 * 让所有课程容量重新变成满额（课程 capacity 本身是静态配置，被占用的是报名记录）。
 *
 * 清理范围（只动本学期的过程数据）：
 * - enrollments：本学期（semesterNo ≥ 学生当前学期）的报名记录
 * - waitlists：候补队列（无学期字段，且保留会串进新学期的递补，直接清空）
 * - drop_requests：仅待处理（pending）的申请，已处理的历史申请保留
 *
 * **历史学期（semesterNo &lt; 学生当前学期）的报名记录一条不删**，
 * 历年选课记录 / 成绩 / 学分累计照常可查；grades 表本身也从不清理。
 */
@Service
public class CapacityService {

    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private DropRequestRepository dropRequestRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository sysRepo;

    /** 释放本学期容量：清掉本学期报名、候补、待处理退课申请；历史学期报名原样保留 */
    @Transactional
    public Map<String, Object> releaseAll() {
        SystemConfig sys = sysRepo.findById(1L).orElse(null);
        int offset = sys == null || sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset();

        // 每个学生当前学期序（不同年级不同，必须逐个算，不能用全局值）
        Map<Long, Integer> curSemByStudent = new HashMap<>();
        for (User u : userRepo.findByRole("student")) {
            curSemByStudent.put(u.getId(),
                    GraduationService.currentSemesterNo(u.getGrade(), LocalDate.now(), offset));
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
