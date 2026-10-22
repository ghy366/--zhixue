package com.zhixue.service;

import com.zhixue.entity.*;
import com.zhixue.repository.*;
import com.zhixue.util.CourseGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EnrollmentService {

    public static final int POINTS_PER_ROUND = 50;

    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private WaitlistService waitlistService;
    @Autowired private NotificationService notificationService;
    @Autowired private RoundScheduleService roundScheduleService;
    @Autowired private StaggerService staggerService;

    public SystemConfig getSystemConfig() {
        // 每次读取都按预设时间窗口校准一次：到点自动开放 / 自动结束
        SystemConfig c = roundScheduleService.sync();
        if (c.getConfirmMinutes() == null) { c.setConfirmMinutes(30); c = sysRepo.save(c); }
        return c;
    }

    // 学生在某轮已用积分：已报名(pending) + 已录取(approved) 之和
    // 报名即冻结积分，未录取时自动退还，保证"积分竞价"公平
    public int getUsedPoints(Long studentId, int round) {
        return enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus()) || "pending".equals(e.getStatus()))
                .mapToInt(Enrollment::getPoints)
                .sum();
    }

    public int getRemainingPoints(Long studentId, int round) {
        return POINTS_PER_ROUND - getUsedPoints(studentId, round);
    }

    public Enrollment getEnrollment(Long studentId, Long courseId, int round) {
        return enrollmentRepo.findByStudentIdAndCourseIdAndRound(studentId, courseId, round).orElse(null);
    }

    /** 所有必修课：由教学计划既定，系统自动安排，不走选课流程 */
    public List<Course> requiredCourses() {
        return courseRepo.findAll().stream()
                .filter(c -> c.getCategory() != null && c.getCategory().contains("必修"))
                .sorted(Comparator.comparing(Course::getId))
                .toList();
    }

    public boolean isRequiredCourse(Long courseId) {
        Course c = courseId == null ? null : courseRepo.findById(courseId).orElse(null);
        return isRequired(c);
    }

    /** 已加载课程实体的必修判断，避免在锁内重复查库 */
    private boolean isRequired(Course c) {
        return c != null && c.getCategory() != null && c.getCategory().contains("必修");
    }

    /** 必修课自动安排：为学生补齐必修课记录（不占积分、直接录取）。幂等，可重复调用 */
    @Transactional
    public int ensureRequiredEnrollments(Long studentId) {
        if (studentId == null) return 0;
        // 只有学生才自动排必修课；教师/管理员（如 admin 打开"我的课程"）不补录，避免脏数据
        User stu = userRepo.findById(studentId).orElse(null);
        if (stu == null || !"student".equals(stu.getRole())) return 0;
        int round = getSystemConfig().getCurrentRound();
        int added = 0;
        for (Course c : requiredCourses()) {
            Enrollment exist = getEnrollment(studentId, c.getId(), round);
            if (exist == null) {
                Enrollment e = new Enrollment();
                e.setStudentId(studentId);
                e.setCourseId(c.getId());
                e.setRound(round);
                e.setPoints(0);
                e.setStatus("approved");
                enrollmentRepo.save(e);
                added++;
            } else if (!"approved".equals(exist.getStatus())) {
                exist.setStatus("approved");
                exist.setPoints(0);
                enrollmentRepo.save(exist);
            }
        }
        return added;
    }

    // 课程报名统计
    public Map<String, Integer> courseStat(Long courseId, int round) {
        List<Enrollment> list = enrollmentRepo.findByCourseIdAndRound(courseId, round);
        int total = (int) list.stream().filter(e -> !"dropped".equals(e.getStatus())).count();
        int pending = (int) list.stream().filter(e -> "pending".equals(e.getStatus())).count();
        int approved = (int) list.stream().filter(e -> "approved".equals(e.getStatus())).count();
        int rejected = (int) list.stream().filter(e -> "rejected".equals(e.getStatus())).count();
        Map<String, Integer> m = new HashMap<>();
        m.put("total", total); m.put("pending", pending);
        m.put("approved", approved); m.put("rejected", rejected);
        return m;
    }

    public boolean isOversubscribed(Long courseId, int round) {
        Course c = courseRepo.findById(courseId).orElse(null);
        if (c == null) return false;
        Map<String, Integer> stat = courseStat(courseId, round);
        return stat.get("total") > c.getCapacity();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> enroll(Long studentId, Long courseId, int points) {
        Map<String, Object> result = new HashMap<>();
        if (studentId == null) {
            result.put("success", false); result.put("msg", "登录已失效，请重新登录"); return result;
        }
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();

        if (!sys.getRoundOpen()) {
            result.put("success", false); result.put("msg", "当前选课已关闭"); return result;
        }

        // 先锁学生行、再锁课程行：同一学生的并发请求与同一门课的并发抢名额都在锁内串行化。
        // 事务使用 READ_COMMITTED，锁内后续查询看到的是其他事务已提交的最新数据。
        User stu0 = userRepo.findByIdForUpdate(studentId).orElse(null);
        if (stu0 == null) {
            result.put("success", false); result.put("msg", "用户不存在，请重新登录"); return result;
        }
        if (!"student".equals(stu0.getRole())) {
            result.put("success", false); result.put("msg", "仅学生可以选课"); return result;
        }

        // 错峰开放校验（仅第 1、2 轮积分制生效；第三轮先到先得不错峰）
        if (round < 3) {
            String gateMsg = staggerService.checkGate(round, stu0.getGrade());
            if (gateMsg != null) { result.put("success", false); result.put("msg", gateMsg); return result; }
        }
        Course course = courseRepo.findByIdForUpdate(courseId).orElse(null);
        if (course == null) { result.put("success", false); result.put("msg", "课程不存在"); return result; }

        if (isRequired(course)) {
            result.put("success", false); result.put("msg", "必修课已自动安排进课表，无需选课"); return result;
        }

        // 已报名检查
        Enrollment existing = getEnrollment(studentId, courseId, round);
        if (existing != null && !"rejected".equals(existing.getStatus()) && !"dropped".equals(existing.getStatus())) {
            result.put("success", false); result.put("msg", "你已报名该课程"); return result;
        }

        // 第三轮：先到先得补选，无需（且忽略）积分
        boolean firstCome = round >= 3;
        // 积分检查：0-50 分自由投入，0 分表示不投入积分（仅第 1、2 轮参与积分竞价）
        int remaining = getRemainingPoints(studentId, round);
        if (!firstCome) {
            if (points < 0) { result.put("success", false); result.put("msg", "投入积分不能为负数"); return result; }
            if (points > POINTS_PER_ROUND) { result.put("success", false); result.put("msg", "单门课程最多投入 " + POINTS_PER_ROUND + " 分"); return result; }
            if (points > remaining) { result.put("success", false); result.put("msg", "积分不足，本轮剩余 " + remaining + " 分"); return result; }
        } else {
            points = 0;
        }

        // 时间冲突检查（与已录取课程）
        List<Enrollment> myApproved = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus())).toList();
        for (Enrollment e : myApproved) {
            Course ec = courseRepo.findById(e.getCourseId()).orElse(null);
            if (ec != null && hasTimeConflict(course, ec)) {
                result.put("success", false); result.put("msg", "与已录取课程时间冲突"); return result;
            }
        }


        // 每学期学分上限（必修课已在上面直接返回，这里只处理选修）
        int myCredit = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .map(e -> courseRepo.findById(e.getCourseId()).map(Course::getCredit).orElse(0))
                .mapToInt(x -> x == null ? 0 : x).sum();
        int courseCredit = course.getCredit() == null ? 0 : course.getCredit();
        Integer maxC = sys.getMaxCreditPerSemester();
        if (maxC != null && maxC > 0 && myCredit + courseCredit > maxC) {
            result.put("success", false);
            result.put("msg", "超出本学期学分上限 " + maxC + " 分，当前已修 " + myCredit + " 分，本课后 " + (myCredit + courseCredit) + " 分");
            return result;
        }

        // 第三轮补选是立即录取，必须在入口就按“毕业要求”封顶，防止名额被多修的学生占走。
        // 第 1、2 轮只登记 pending，故意不在这里拦截：学生超报去竞价，由结算按“时间冲突/毕业要求”统一裁决。
        if (firstCome) {
            String group = CourseGroup.groupOf(course.getCategory());
            int groupReq = gradReqOf(group, sys);
            if (groupReq > 0) {
                Map<String, Integer> groupEarned = gradGroupEarned(studentId, null, null);
                int used = groupEarned.getOrDefault(group, 0);
                if (used + courseCredit > groupReq) {
                    result.put("success", false);
                    result.put("msg", "「" + group + "」类毕业学分已达上限 " + groupReq + " 分（已修 " + used + " 分），无法再修《" + course.getName() + "》");
                    return result;
                }
            }
        }

        // 第三轮：先到先得补选，立即录取（名额满即止），无需等待结算。
        // 此时已持有课程行锁，统计与插入不会被其他报名事务交叉，容量不会被突破。
        if (firstCome) {
            int approved = (int) enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                    .filter(e -> "approved".equals(e.getStatus())).count();
            int cap = course.getCapacity() == null ? 0 : course.getCapacity();
            if (approved >= cap) {
                result.put("success", false);
                result.put("msg", "本轮为补选，该课程名额已满，无法报名");
                return result;
            }
        }

        // 复用已存在的记录（退选 / 落选后重新报名）：
        // 不能再 delete + insert —— Hibernate 在同一事务里 insert 先于 delete 执行，
        // 会撞上唯一键 uk_enrollment_student_course_round 直接 500。
        Enrollment enr = existing;
        if (enr == null) {
            enr = new Enrollment();
            enr.setStudentId(studentId);
            enr.setCourseId(courseId);
            enr.setRound(round);
        }
        enr.setPoints(points);
        enr.setStatus(firstCome ? "approved" : "pending");
        enrollmentRepo.save(enr);

        if (firstCome) {
            Map<String, Integer> stat = courseStat(courseId, round);
            int total = stat.get("total");
            int cap = course.getCapacity() == null ? 0 : course.getCapacity();
            result.put("success", true);
            result.put("msg", "补选成功，已录取《" + course.getName() + "》。当前 " + total + "/" + cap + " 人。");
        } else {
            // 选课期间只登记报名，本轮结算时按投入积分统一录取（同分随机抽签）
            Map<String, Integer> stat = courseStat(courseId, round);
            int total = stat.get("total");
            int cap = course.getCapacity() == null ? 0 : course.getCapacity();
            result.put("success", true);
            result.put("msg", (points > 0 ? "报名成功！已投入 " + points + " 积分，" : "报名成功！未投入积分，")
                    + "当前报名 " + total + "/" + cap + " 人。"
                    + (total > cap
                        ? "该课程已超额，本轮结束后按投入积分从高到低录取，同分随机抽签"
                        : "本轮结束后统一录取"));
        }
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> drop(Long studentId, Long courseId) {
        Map<String, Object> result = new HashMap<>();
        if (studentId == null) {
            result.put("success", false); result.put("msg", "登录已失效，请重新登录"); return result;
        }
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();

        // 锁学生与课程：避免同一学生重复退课，或与结算/候补递补并发修改同一门课
        if (userRepo.findByIdForUpdate(studentId).isEmpty()) {
            result.put("success", false); result.put("msg", "用户不存在，请重新登录"); return result;
        }
        Course course = courseRepo.findByIdForUpdate(courseId).orElse(null);
        if (isRequired(course)) {
            result.put("success", false);
            result.put("msg", "必修课由教学计划自动安排，不能退课");
            return result;
        }
        Enrollment enr = getEnrollment(studentId, courseId, round);
        if (enr == null) { result.put("success", false); result.put("msg", "未找到选课记录"); return result; }
        if ("rejected".equals(enr.getStatus()) || "dropped".equals(enr.getStatus())) {
            result.put("success", false); result.put("msg", "该课程未录取"); return result;
        }
        String wasStatus = enr.getStatus();
        enr.setStatus("dropped");
        enrollmentRepo.save(enr);
        // 已录取课程被退 → 释放名额并自动递补候补队列下一位
        if ("approved".equals(wasStatus)) waitlistService.promoteIfNeeded(courseId, round);
        result.put("success", true);
        result.put("msg", "pending".equals(wasStatus) ? "已撤销报名，积分已退还" : "退课成功，积分已退还");
        return result;
    }

    /** 改选：退掉旧课（释放名额与积分）并报名新课；若配置改选惩罚，惩罚积分计入新课投入（从总积分中消耗） */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> reselect(Long studentId, Long oldCourseId, Long newCourseId, int points) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (studentId == null || oldCourseId == null || newCourseId == null) {
            result.put("success", false); result.put("msg", "参数不完整"); return result;
        }
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();
        if (!sys.getRoundOpen()) {
            result.put("success", false); result.put("msg", "当前轮次未开放，无法改选"); return result;
        }
        if (oldCourseId.equals(newCourseId)) {
            result.put("success", false); result.put("msg", "请选择与原课程不同的课程"); return result;
        }
        if (userRepo.findByIdForUpdate(studentId).isEmpty()) {
            result.put("success", false); result.put("msg", "用户不存在，请重新登录"); return result;
        }

        // 按课程 ID 升序加锁，避免两个学生互相改选时形成 A→B / B→A 的死锁
        Long firstId = Math.min(oldCourseId, newCourseId);
        Long secondId = Math.max(oldCourseId, newCourseId);
        Course first = courseRepo.findByIdForUpdate(firstId).orElse(null);
        Course second = courseRepo.findByIdForUpdate(secondId).orElse(null);
        Course oldCourse = oldCourseId.equals(firstId) ? first : second;
        Course nc = newCourseId.equals(firstId) ? first : second;

        if (isRequired(oldCourse)) {
            result.put("success", false); result.put("msg", "必修课由教学计划自动安排，不能改选"); return result;
        }
        Enrollment old = getEnrollment(studentId, oldCourseId, round);
        if (old == null || "dropped".equals(old.getStatus()) || "rejected".equals(old.getStatus())) {
            result.put("success", false); result.put("msg", "原课程状态不可改选"); return result;
        }
        if (nc == null) { result.put("success", false); result.put("msg", "新课程不存在"); return result; }
        if (isRequired(nc)) {
            result.put("success", false); result.put("msg", "必修课由教学计划自动安排，无需选课"); return result;
        }
        Enrollment existNew = getEnrollment(studentId, newCourseId, round);
        if (existNew != null && !"rejected".equals(existNew.getStatus()) && !"dropped".equals(existNew.getStatus())) {
            result.put("success", false); result.put("msg", "你已报名该课程，无需改选"); return result;
        }
        int penalty = sys.getReselectPenalty() == null ? 0 : sys.getReselectPenalty();
        int effective = points + penalty;
        String oldStatus = old.getStatus();
        // 先退旧课（释放名额与积分）
        old.setStatus("dropped");
        enrollmentRepo.save(old);
        // 报名新课（惩罚积分计入新课投入，从总积分中消耗）
        Map<String, Object> enr = enroll(studentId, newCourseId, effective);
        if (!(Boolean) enr.get("success")) {
            Enrollment rollback = getEnrollment(studentId, oldCourseId, round);
            if (rollback != null) { rollback.setStatus(oldStatus); enrollmentRepo.save(rollback); }
            result.put("success", false);
            result.put("msg", (String) enr.get("msg"));
            return result;
        }
        // 新课报名成功后再释放旧课名额并递补候补，避免新课失败时旧课名额白白让出
        if ("approved".equals(oldStatus)) waitlistService.promoteIfNeeded(oldCourseId, round);
        result.put("success", true);
        result.put("penalty", penalty);
        String oldName = oldCourse != null ? oldCourse.getName() : "原课程";
        result.put("msg", "改选成功！已退掉《" + oldName + "》，改报《" + nc.getName() + "》"
                + (penalty > 0 ? "，本次改选消耗 " + penalty + " 积分惩罚" : ""));
        return result;
    }

    // 老师审批
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> approve(Long courseId, List<Long> selectedStudentIds) {
        SystemConfig sys = getSystemConfig();
        // 与结算共用配置行锁，避免“审批”和“结算”同时改同一批报名记录
        sysRepo.findByIdForUpdate(1L);
        int round = sys.getCurrentRound();
        Map<String, Object> result = new HashMap<>();
        Course course = courseRepo.findByIdForUpdate(courseId).orElse(null);
        if (course == null) { result.put("success", false); result.put("msg", "课程不存在"); return result; }
        if (selectedStudentIds.size() > course.getCapacity()) {
            result.put("success", false); result.put("msg", "录取人数不能超过容量 " + course.getCapacity()); return result;
        }
        List<Enrollment> list = enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> !"dropped".equals(e.getStatus())).toList();
        for (Enrollment e : list) {
            if (selectedStudentIds.contains(e.getStudentId())) {
                if (!"approved".equals(e.getStatus())) notifyResult(e, course, true, null);
                e.setStatus("approved");
            } else if ("approved".equals(e.getStatus()) || "pending".equals(e.getStatus())) {
                notifyResult(e, course, false, null);
                e.setStatus("rejected");
            }
        }
        enrollmentRepo.saveAll(list);
        result.put("success", true);
        result.put("msg", "审批完成，已录取 " + selectedStudentIds.size() + " 人");
        return result;
    }

    /**
     * 结算某一轮选课。
     * <p>
     * 与旧实现相比有三点关键变化：
     * 1. 所有 pending 报名按积分全局降序（同分随机）统一分配，而不是逐门课各自分配，
     *    这样同一学生同时冲突/超学分时，会优先保留其投入积分更高的课程；
     * 2. 录取时重新校验时间冲突和学期学分上限，避免报名阶段只统计 approved 造成的“先报名、后冲突”；
     * 3. 以 system_config 单例行作为全局锁，手动结算和定时结算不会并发执行。
     * 必修课由教学计划自动安排，不参与结算。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> settleRound(Integer round) {
        SystemConfig sys = getSystemConfig();
        // 全局互斥：拿到配置行锁后才开始读取/修改报名记录，另一个结算事务会在此等待
        sysRepo.findByIdForUpdate(1L);
        if (round == null) round = sys.getCurrentRound();

        Map<Long, Course> courseById = new LinkedHashMap<>();
        for (Course c : courseRepo.findAll()) courseById.put(c.getId(), c);

        List<Enrollment> all = enrollmentRepo.findByRound(round);

        // 已录取课程（含必修课）按学生归档，用于冲突检测与学分累计
        Map<Long, Map<Long, Course>> approvedByStudent = new HashMap<>();
        Map<Long, Integer> creditByStudent = new HashMap<>();
        // 各学生在“毕业大类”（必修/选修/通识/实践）上已修的学分，懒加载并缓存。
        // 学分封顶不能只看本学期，毕业要求是跨学期的总量，所以这里要统计该学生全部已录取课程。
        Map<Long, Map<String, Integer>> groupEarnedByStudent = new HashMap<>();
        for (Enrollment e : all) {
            if (!"approved".equals(e.getStatus())) continue;
            Course c = courseById.get(e.getCourseId());
            if (c == null) continue;
            Map<Long, Course> approved = approvedByStudent.computeIfAbsent(e.getStudentId(), k -> new LinkedHashMap<>());
            if (approved.putIfAbsent(c.getId(), c) == null) {
                creditByStudent.merge(e.getStudentId(), nz(c.getCredit()), Integer::sum);
            }
        }

        // 本轮待录取的选修课报名
        List<Enrollment> candidates = new ArrayList<>();
        for (Enrollment e : all) {
            if (!"pending".equals(e.getStatus())) continue;
            Course c = courseById.get(e.getCourseId());
            if (c == null || isRequired(c)) continue;
            candidates.add(e);
        }

        // 先随机打乱，再稳定按积分降序：同分者的相对顺序即随机抽签结果
        Collections.shuffle(candidates);
        candidates.sort((a, b) -> Integer.compare(nz(b.getPoints()), nz(a.getPoints())));

        Map<Long, Integer> applied = new LinkedHashMap<>();
        Map<Long, Integer> admitted = new LinkedHashMap<>();
        Map<Long, Integer> cutline = new LinkedHashMap<>();
        Map<Long, Map<String, Integer>> rejectReasons = new LinkedHashMap<>();
        Set<String> processed = new HashSet<>();
        int admittedCount = 0, rejectedCount = 0;

        for (Enrollment e : candidates) {
            Course c = courseById.get(e.getCourseId());
            Long cid = c.getId();
            Long sid = e.getStudentId();
            applied.merge(cid, 1, Integer::sum);

            Map<Long, Course> approved = approvedByStudent.computeIfAbsent(sid, k -> new LinkedHashMap<>());
            Map<String, Integer> groupEarned = gradGroupEarned(sid, courseById, groupEarnedByStudent);
            String reason = settleRejectReason(e, c, approved, processed, admitted,
                    sys, creditByStudent, groupEarned);

            if (reason == null) {
                e.setStatus("approved");
                admitted.merge(cid, 1, Integer::sum);
                cutline.put(cid, nz(e.getPoints()));
                approved.put(cid, c);
                creditByStudent.merge(sid, nz(c.getCredit()), Integer::sum);
                // 本次录取的学分也要计入该大类，后续候选课才能正确判断是否毕业超修
                groupEarned.merge(CourseGroup.groupOf(c.getCategory()), nz(c.getCredit()), Integer::sum);
                admittedCount++;
            } else {
                e.setStatus("rejected");
                rejectedCount++;
                rejectReasons.computeIfAbsent(cid, k -> new LinkedHashMap<>()).merge(reason, 1, Integer::sum);
            }
            notifyResult(e, c, reason == null, reason);
        }
        enrollmentRepo.saveAll(candidates);

        List<Map<String, Object>> details = new ArrayList<>();
        courseById.values().stream()
                .filter(c -> applied.containsKey(c.getId()))
                .sorted(Comparator.comparing(Course::getId))
                .forEach(c -> {
                    int cap = nz(c.getCapacity());
                    int a = applied.getOrDefault(c.getId(), 0);
                    int ok = admitted.getOrDefault(c.getId(), 0);
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("courseId", c.getId());
                    d.put("courseName", c.getName());
                    d.put("capacity", cap);
                    d.put("applied", a);
                    d.put("admitted", ok);
                    d.put("rejected", a - ok);
                    // 只有存在未录取者时才展示“最低录取分”，全部录取则显示为空（前端显示“全部录取”）
                    d.put("cutline", a > ok && ok > 0 ? cutline.get(c.getId()) : null);
                    Map<String, Integer> rr = rejectReasons.get(c.getId());
                    if (rr != null && !rr.isEmpty()) d.put("rejectReasons", rr);
                    details.add(d);
                });

        Map<String, Object> r = new HashMap<>();
        r.put("success", true);
        r.put("msg", "第 " + round + " 轮结算完成：录取 " + admittedCount + " 人次，未录取 " + rejectedCount + " 人次（未录取积分已退还）");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", round);
        data.put("admitted", admittedCount);
        data.put("rejected", rejectedCount);
        data.put("details", details);
        r.put("data", data);
        return r;
    }

    private int nz(Integer v) { return v == null ? 0 : v; }

    /**
     * 结算时判断一条报名为什么不能录取。返回 null 表示可以录取。
     * 判断顺序与错误提示保持一致：重复 → 容量 → 时间冲突 → 学分上限 → 毕业要求。
     */
    private String settleRejectReason(Enrollment e, Course c, Map<Long, Course> approved,
                                      Set<String> processed, Map<Long, Integer> admitted,
                                      SystemConfig sys, Map<Long, Integer> creditByStudent,
                                      Map<String, Integer> groupEarned) {
        if (!processed.add(e.getStudentId() + ":" + c.getId()) || approved.containsKey(c.getId())) {
            return "重复报名";
        }
        if (admitted.getOrDefault(c.getId(), 0) >= nz(c.getCapacity())) {
            return "容量已满";
        }
        if (hasTimeConflict(c, approved.values())) {
            return "与已录取课程时间冲突";
        }
        Integer maxC = sys.getMaxCreditPerSemester();
        if (maxC != null && maxC > 0
                && creditByStudent.getOrDefault(e.getStudentId(), 0) + nz(c.getCredit()) > maxC) {
            return "超出本学期学分上限 " + maxC + " 分";
        }
        // 按毕业要求封顶：该大类已修学分 + 本课学分 > 毕业要求 → 拒绝（多修不算毕业学分，也不该占用名额）
        String group = CourseGroup.groupOf(c.getCategory());
        int groupReq = gradReqOf(group, sys);
        if (groupReq > 0 && groupEarned.getOrDefault(group, 0) + nz(c.getCredit()) > groupReq) {
            return "「" + group + "」类毕业学分已达上限 " + groupReq + " 分";
        }
        return null;
    }

    /** 毕业要求中某一类学分的上限（null/0 表示不限制） */
    private int gradReqOf(String group, SystemConfig sys) {
        Integer v = switch (group) {
            case "必修" -> sys.getGradReqRequired();
            case "通识" -> sys.getGradReqGeneral();
            case "实践" -> sys.getGradReqPractice();
            default -> sys.getGradReqElective();   // 选修
        };
        return v == null ? 0 : v;
    }

    /**
     * 该学生在毕业大类上已修学分（跨轮次已录取记录、按课程去重）。
     * {@code groupEarnedByStudent} 是结算调用方传入的缓存，同一学生只查一次。
     * {@code courseCache} 传结算里 courseRepo.findAll 的缓存；传 null 时逐条查库（选课入口使用）。
     */
    private Map<String, Integer> gradGroupEarned(Long studentId, Map<Long, Course> courseCache,
                                                 Map<Long, Map<String, Integer>> cacheByStudent) {
        if (cacheByStudent == null) {
            return gradGroupEarnedUncached(studentId, courseCache);
        }
        return cacheByStudent.computeIfAbsent(studentId, sid -> gradGroupEarnedUncached(sid, courseCache));
    }

    private Map<String, Integer> gradGroupEarnedUncached(Long studentId, Map<Long, Course> courseCache) {
        Map<String, Integer> earned = new LinkedHashMap<>();
        Set<Long> seenCourses = new HashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            if (!seenCourses.add(e.getCourseId())) continue;   // 同一门课多轮都录取过只算一次学分
            Course c = courseCache != null
                    ? courseCache.get(e.getCourseId())
                    : courseRepo.findById(e.getCourseId()).orElse(null);
            if (c == null) continue;
            earned.merge(CourseGroup.groupOf(c.getCategory()), nz(c.getCredit()), Integer::sum);
        }
        return earned;
    }

    /** 审批/结算结果 → 站内消息推送 */
    private void notifyResult(Enrollment e, Course c, boolean approved, String reason) {
        try {
            String detail = approved
                    ? "已被录取，请按时上课"
                    : "未被录取" + (reason == null || reason.isBlank() ? "" : "（" + reason + "）") + "，所投积分已退还";
            notificationService.notify(e.getStudentId(), "enroll",
                    approved ? "🎉 选课录取通知" : "选课结果通知",
                    "《" + c.getName() + "》" + detail,
                    e.getCourseId());
        } catch (Exception ignore) {}
    }

    /** 与一组已录取课程做冲突检测 */
    private boolean hasTimeConflict(Course course, Collection<Course> others) {
        for (Course other : others) {
            if (other != null && !other.getId().equals(course.getId()) && hasTimeConflict(course, other)) {
                return true;
            }
        }
        return false;
    }

    // 时间冲突检测
    private boolean hasTimeConflict(Course a, Course b) {
        List<Map<String, String>> slotsA = parseTimeSlots(a.getTime());
        List<Map<String, String>> slotsB = parseTimeSlots(b.getTime());
        for (Map<String, String> sa : slotsA) {
            for (Map<String, String> sb : slotsB) {
                if (sa.get("day").equals(sb.get("day"))) {
                    int aS = toMin(sa.get("start")), aE = toMin(sa.get("end"));
                    int bS = toMin(sb.get("start")), bE = toMin(sb.get("end"));
                    if (aS < bE && bS < aE) return true;
                }
            }
        }
        return false;
    }

    private List<Map<String, String>> parseTimeSlots(String timeStr) {
        List<Map<String, String>> slots = new ArrayList<>();
        if (timeStr == null) return slots;
        String[] parts = timeStr.split("/");
        Pattern p = Pattern.compile("(周[一二三四五六日])\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})");
        for (String part : parts) {
            Matcher m = p.matcher(part.trim());
            if (m.find()) {
                Map<String, String> s = new HashMap<>();
                s.put("day", m.group(1)); s.put("start", m.group(2)); s.put("end", m.group(3));
                slots.add(s);
            }
        }
        return slots;
    }

    private int toMin(String t) {
        String[] p = t.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** 批量选课（一次投多门）。部分失败的返回明细，事务逐门处理 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> enrollBatch(Long studentId, List<Long> courseIds, int pointsPerCourse) {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();
        int success = 0, fail = 0;
        // 按课程 ID 升序加锁，避免两个学生分别批量选 [A,B] / [B,A] 时互相等待形成死锁
        List<Long> ordered = new ArrayList<>(courseIds);
        ordered.removeIf(Objects::isNull);
        ordered.sort(Comparator.naturalOrder());
        for (Long cid : ordered) {
            Map<String, Object> one = enroll(studentId, cid, pointsPerCourse);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", cid);
            item.put("success", one.get("success"));
            item.put("msg", one.get("msg"));
            details.add(item);
            if (Boolean.TRUE.equals(one.get("success"))) success++; else fail++;
        }
        r.put("success", fail == 0);
        r.put("msg", fail == 0 ? ("批量报名成功，共 " + success + " 门")
                              : ("部分失败：成功 " + success + " 门，失败 " + fail + " 门"));
        r.put("data", details);
        return r;
    }

    /** 批量退课 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> dropBatch(Long studentId, List<Long> courseIds) {
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> details = new ArrayList<>();
        int success = 0, fail = 0;
        // 与批量选课一致：固定加锁顺序，避免交叉死锁
        List<Long> ordered = new ArrayList<>(courseIds);
        ordered.removeIf(Objects::isNull);
        ordered.sort(Comparator.naturalOrder());
        for (Long cid : ordered) {
            Map<String, Object> one = drop(studentId, cid);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", cid);
            item.put("success", one.get("success"));
            item.put("msg", one.get("msg"));
            details.add(item);
            if (Boolean.TRUE.equals(one.get("success"))) success++; else fail++;
        }
        r.put("success", fail == 0);
        r.put("msg", fail == 0 ? ("批量退课成功，共 " + success + " 门")
                              : ("部分失败：成功 " + success + " 门，失败 " + fail + " 门"));
        r.put("data", details);
        return r;
    }
}
