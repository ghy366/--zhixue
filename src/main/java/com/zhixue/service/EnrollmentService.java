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
    @Autowired private GradeRepository gradeRepo;
    @Autowired private WaitlistService waitlistService;
    @Autowired private NotificationService notificationService;
    @Autowired private RoundScheduleService roundScheduleService;
    @Autowired private StaggerService staggerService;

    public SystemConfig getSystemConfig() {
        // 每次读取都按预设时间窗口校准一次：到点自动开放 / 自动结束
        SystemConfig c = roundScheduleService.sync();
        if (c.getConfirmMinutes() == null) { c.setConfirmMinutes(30); c = sysRepo.save(c); }
        // 旧库行新增的门数上限列默认为 NULL（Hibernate 加列不回填 Java 默认值），归一化为默认 2
        if (c.getMaxCoursesPerSemester() == null) { c.setMaxCoursesPerSemester(2); c = sysRepo.save(c); }
        // 旧库行新增的教师授课上限列默认为 NULL，归一化为 3
        if (c.getTeacherMaxCourses() == null) { c.setTeacherMaxCourses(3); c = sysRepo.save(c); }
        if (c.getSemesterOffset() == null) { c.setSemesterOffset(0); c = sysRepo.save(c); }
        return c;
    }

    // 学生在某轮已用积分：已报名(pending) + 已录取(approved) 之和
    // 报名即冻结积分，未录取时自动退还，保证"积分竞价"公平
    public int getUsedPoints(Long studentId, int round) {
        // 积分池为「每学期 50 分」：只统计本学期（修读学期序 = 当前学期序）的选修投分，
        // 历史学期的投分随学期归档不占当前池；必修由教学计划自动安排（points 恒 0）
        int curSem = GraduationService.currentSemesterNo(
                userRepo.findById(studentId).map(User::getGrade).orElse(null), java.time.LocalDate.now(),
                getSystemConfig().getSemesterOffset() == null ? 0 : getSystemConfig().getSemesterOffset());
        return enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus()) || "pending".equals(e.getStatus()))
                .filter(e -> !isRequiredCourse(e.getCourseId()))
                .filter(e -> e.getSemesterNo() == null || e.getSemesterNo() == curSem)
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

    /** 已加载课程实体的必修判断，避免在锁内重复查库。统一复用 CourseGroup 的必修分组 */
    private boolean isRequired(Course c) {
        return c != null && CourseGroup.isRequired(c.getCategory());
    }

    /** 必修课自动安排：为学生补齐必修课记录（不占积分、直接录取）。幂等，可重复调用 */
    @Transactional
    public int ensureRequiredEnrollments(Long studentId) {
        if (studentId == null) return 0;
        // 只有学生才自动排必修课；教师/管理员（如 admin 打开"我的课程"）不补录，避免脏数据
        User stu = userRepo.findById(studentId).orElse(null);
        if (stu == null || !"student".equals(stu.getRole())) return 0;
        int round = getSystemConfig().getCurrentRound();
        // 培养方案学期制：必修课按 semesterNo 排定开课学期，学生只自动修读当前学期的必修；
        // 未标注学期序的必修课保持旧行为（每轮全排）
        int curSem = GraduationService.currentSemesterNo(stu.getGrade(), java.time.LocalDate.now(),
                getSystemConfig().getSemesterOffset() == null ? 0 : getSystemConfig().getSemesterOffset());
        int added = 0;
        for (Course c : requiredCourses()) {
            Integer sno = c.getSemesterNo();
            if (sno != null && sno > 0) {
                if (sno > curSem) continue;   // 未来学期的必修：尚未开课
                if (sno < curSem) {
                    // 历史学期必修：仅挂科自动重修（跟班重读）；已通过的不排，未出成绩的等待出分
                    Grade g = gradeRepo.findByStudentIdAndCourseId(studentId, c.getId()).orElse(null);
                    if (g == null || g.getTotalScore() == null || GradeService.passed(g)) continue;
                }
            }
            Enrollment exist = getEnrollment(studentId, c.getId(), round);
            if (exist == null) {
                Enrollment e = new Enrollment();
                e.setStudentId(studentId);
                e.setCourseId(c.getId());
                e.setRound(round);
                e.setPoints(0);
                e.setStatus("approved");
                e.setSemesterNo(curSem);   // 修读记录归属当前学期
                enrollmentRepo.save(e);
                added++;
            } else {
                // 需要排课的必修（当前学期/挂科重修）：行已存在时刷新归属学期与本轮状态
                exist.setStatus("approved");
                exist.setPoints(0);
                exist.setSemesterNo(curSem);
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

    /**
     * 课程入选积分参考：
     * - safeLine：实时竞争线。把本轮所有未退课的投分从高到低排，取容量名次上的分数——
     *   若此刻截止结算，这就是最后一名被录取者的分数。报名未满容量时为 null。
     * - topPoints：当前最高投分，供学生感知竞争烈度。
     * - history：从上一轮往前找最近一轮有报名记录的轮次，给出当时的录取线
     *   （录取最低分，需该轮同时存在落选者；全部录取则 cutline 为 null）。
     */
    public Map<String, Object> pointsGuide(Long courseId, int round) {
        Course c = courseRepo.findById(courseId).orElse(null);
        int capacity = c == null || c.getCapacity() == null ? 0 : c.getCapacity();
        List<Integer> bids = new ArrayList<>();
        for (Enrollment e : enrollmentRepo.findByCourseIdAndRound(courseId, round)) {
            if ("dropped".equals(e.getStatus()) || e.getPoints() == null) continue;
            bids.add(e.getPoints());
        }
        bids.sort(Comparator.reverseOrder());
        Integer safeLine = capacity > 0 && bids.size() >= capacity ? bids.get(capacity - 1) : null;
        int top = bids.isEmpty() ? 0 : bids.get(0);
        // 投分分布直方图：10 桶，每桶 5 分（0-4 / 5-9 / ... / 45-50），含已录取+待录取
        int[] dist = new int[10];
        for (int p : bids) dist[Math.max(0, Math.min(9, p / 5))]++;
        Map<String, Object> distMap = new LinkedHashMap<>();
        distMap.put("bucketSize", 5);
        distMap.put("counts", dist);

        // 历年录取线走势：第 1 轮 ~ 上一轮，每轮的报名/录取线（该轮全部录取时 cutline 为 null）
        List<Map<String, Object>> trend = new ArrayList<>();
        Map<String, Object> history = null;
        for (int r = 1; r <= round - 1; r++) {
            List<Enrollment> prev = enrollmentRepo.findByCourseIdAndRound(courseId, r);
            if (prev.isEmpty()) continue;
            List<Integer> approvedPts = new ArrayList<>();
            int rejected = 0, applied = 0;
            for (Enrollment e : prev) {
                if ("dropped".equals(e.getStatus())) continue;
                applied++;
                if ("rejected".equals(e.getStatus())) rejected++;
                if ("approved".equals(e.getStatus()) && e.getPoints() != null) approvedPts.add(e.getPoints());
            }
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("round", r);
            h.put("applied", applied);
            // 有落选者 → 真实录取线（最低录取分）；全员录取 → 给出当轮最低录取分供参考，fullAdmitted 标记
            h.put("cutline", approvedPts.isEmpty() ? null : Collections.min(approvedPts));
            h.put("fullAdmitted", rejected == 0);
            trend.add(h);
            history = h;
        }

        Map<String, Object> g = new LinkedHashMap<>();
        g.put("capacity", capacity);
        g.put("bidCount", bids.size());
        g.put("topPoints", top);
        g.put("safeLine", safeLine);
        g.put("dist", distMap);
        g.put("history", history);
        g.put("trend", trend);
        return g;
    }

    /**
     * 选课策略模拟：假设我此刻以 points 分报名该课程，按当前报名快照估算录取结果。
     * 纯估算——后续报名者可能改变格局，同分随机抽签，以截止时数据为准。
     */
    public Map<String, Object> simulateAdmission(Long courseId, int points) {
        int round = getSystemConfig().getCurrentRound();
        Course c = courseRepo.findById(courseId).orElse(null);
        int capacity = c == null || c.getCapacity() == null ? 0 : c.getCapacity();
        List<Enrollment> list = enrollmentRepo.findByCourseIdAndRound(courseId, round).stream()
                .filter(e -> !"dropped".equals(e.getStatus())).toList();
        int approved = (int) list.stream().filter(e -> "approved".equals(e.getStatus())).count();
        List<Integer> pendingPts = new ArrayList<>();
        for (Enrollment e : list) {
            if ("pending".equals(e.getStatus()) && e.getPoints() != null) pendingPts.add(e.getPoints());
        }
        Collections.sort(pendingPts, Comparator.reverseOrder());
        int total = pendingPts.size();
        int seatsLeft = Math.max(0, capacity - approved);
        int higher = 0, tie = 0;
        for (int p : pendingPts) {
            if (p > points) higher++;
            else if (p == points) tie++;
        }
        int myRank = higher + 1;                       // 把我插进队列后的名次
        int beatPct = total == 0 ? 0 : (int) Math.round((total - higher - tie) * 100.0 / total);

        int chance;
        if (capacity <= 0) chance = 0;
        else if (total + 1 <= seatsLeft) chance = 100; // 连我在内都没满员
        else {
            int boundarySeats = seatsLeft - higher;    // 截止线分给同分组的名额
            if (boundarySeats >= tie + 1) chance = 100;
            else if (boundarySeats <= 0) chance = 0;
            else chance = (int) Math.round(boundarySeats * 100.0 / (tie + 1));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("capacity", capacity);
        m.put("approved", approved);
        m.put("pending", total);
        m.put("seatsLeft", seatsLeft);
        m.put("myRank", myRank);
        m.put("beatPct", beatPct);
        m.put("chance", chance);
        return m;
    }

    /**
     * 「选了这门课的人也选了」：取与本课程有过录取关系的学生，统计他们录取过的其他选修课，
     * 按共选人数降序取前 n 门（排除必修课与自身）。
     */
    public List<Map<String, Object>> alsoTookCounts(Long courseId, int n) {
        Set<Long> students = new LinkedHashSet<>();
        for (Enrollment e : enrollmentRepo.findByCourseId(courseId)) {
            if ("approved".equals(e.getStatus()) && e.getStudentId() != null) students.add(e.getStudentId());
        }
        Map<Long, Integer> freq = new HashMap<>();
        for (Long sid : students) {
            Set<Long> mine = new LinkedHashSet<>();
            for (Enrollment e : enrollmentRepo.findByStudentId(sid)) {
                if (!"approved".equals(e.getStatus())) continue;
                Long cid = e.getCourseId();
                if (cid == null || cid.equals(courseId)) continue;
                mine.add(cid);   // 按学生去重：同一学生多轮录取同一门课只算 1 次
            }
            for (Long cid : mine) freq.merge(cid, 1, Integer::sum);
        }
        // 先剔除必修课再取 Top N：必修课人人自动排课、共选数必然虚高，会把选修挤出前 N
        List<Map.Entry<Long, Integer>> top = freq.entrySet().stream()
                .filter(t -> {
                    Course c = courseRepo.findById(t.getKey()).orElse(null);
                    return c != null && !isRequiredCourse(t.getKey());
                })
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(Math.max(1, n))
                .toList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, Integer> t : top) {
            Course c = courseRepo.findById(t.getKey()).orElse(null);
            if (c == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("name", c.getName());
            m.put("category", c.getCategory());
            m.put("credit", c.getCredit());
            m.put("teacherId", c.getTeacherId());
            m.put("time", c.getTime());
            m.put("count", t.getValue());
            out.add(m);
        }
        return out;
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

        // 已通过的课程不允许重复修读；挂科课程放行（重修，成绩覆盖）——真实高校「先补考、再不过重修」模式
        if (passedCourseIds(studentId).contains(courseId)) {
            result.put("success", false); result.put("msg", "该课程已通过，无需重修"); return result;
        }

        // 先修课校验：未通过先修课不可报名（真实高校培养方案的先修约束）
        if (course.getPrereqId() != null) {
            Grade preGrade = gradeRepo.findByStudentIdAndCourseId(studentId, course.getPrereqId()).orElse(null);
            if (!GradeService.passed(preGrade)) {
                Course pre = courseRepo.findById(course.getPrereqId()).orElse(null);
                result.put("success", false);
                result.put("msg", "需先通过《" + (pre != null ? pre.getName() : "先修课程") + "》才能选《" + course.getName() + "》");
                return result;
            }
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

        // 时间冲突检查：基准为「本学期」已录取课程（修读学期序 = 当前学期序、跨轮次、按课程去重）——
        // 历史学期的课已经结课，不再占用本学期课表
        int mySem = GraduationService.currentSemesterNo(
                userRepo.findById(studentId).map(User::getGrade).orElse(null), java.time.LocalDate.now(),
                sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset());
        Set<Long> seenApproved = new HashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            if (e.getSemesterNo() == null || e.getSemesterNo() != mySem) continue;
            if (!seenApproved.add(e.getCourseId())) continue;
            Course ec = courseRepo.findById(e.getCourseId()).orElse(null);
            if (ec != null && hasTimeConflict(course, ec)) {
                result.put("success", false); result.put("msg", "与已录取课程《" + ec.getName() + "》时间冲突"); return result;
            }
        }

        // 每学期选课门数上限：只统计非必修（选修/通识/实践）的已录取课程（按课程去重，退课/落选不计）。
        // 必修课由教学计划自动安排、学生不可控，不占用门数额度。
        // 挂科课程不占额度（允许重修或改选新课）；本轮正在重修的挂科课仍计入门数（占用课表）。
        Integer maxCourses = sys.getMaxCoursesPerSemester();
        if (maxCourses != null && maxCourses > 0) {
            Set<Long> failed = failedCourseIds(studentId);
            Set<Long> retakingNow = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                    .filter(e -> "approved".equals(e.getStatus()))
                    .map(Enrollment::getCourseId)
                    .filter(failed::contains)
                    .collect(java.util.stream.Collectors.toSet());
            long myCourses = enrollmentRepo.findByStudentId(studentId).stream()
                    .filter(e -> "approved".equals(e.getStatus()))
                    .filter(e -> e.getSemesterNo() != null && e.getSemesterNo() == mySem)   // 本学期口径：历史学期已结课不占额度
                    .map(Enrollment::getCourseId).distinct()
                    .filter(cid -> {
                        if (failed.contains(cid) && !retakingNow.contains(cid)) return false;
                        Course ec = courseRepo.findById(cid).orElse(null);
                        // 课程已删除的存量记录按选修计入（保守口径）；必修不占额度
                        return ec == null || !CourseGroup.isRequired(ec.getCategory());
                    }).count();
            if (myCourses >= maxCourses) {
                result.put("success", false);
                result.put("msg", "本学期最多可选 " + maxCourses + " 门选修课，已选 " + myCourses + " 门，无法再报名《" + course.getName() + "》");
                return result;
            }
        }


        // 每学期学分上限（必修课已在上面直接返回，这里只处理选修）。
        // 口径：必修学分由教学计划自动安排、学生不可控，不占选修学分上限；
        // 已修学分跨轮次累计——轮次是同一学期内的批次，第 1 轮录的课同样占用本学期额度。
        int myCredit = enrollmentRepo.findByStudentId(studentId).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .filter(e -> e.getSemesterNo() == null || e.getSemesterNo() == mySem)
                .map(e -> courseRepo.findById(e.getCourseId()).orElse(null))
                .filter(ec -> ec != null && !CourseGroup.isRequired(ec.getCategory()))
                .mapToInt(ec -> ec.getCredit() == null ? 0 : ec.getCredit()).sum();
        int courseCredit = course.getCredit() == null ? 0 : course.getCredit();
        Integer maxC = sys.getMaxCreditPerSemester();
        if (maxC != null && maxC > 0 && myCredit + courseCredit > maxC) {
            result.put("success", false);
            result.put("msg", "超出本学期选修学分上限 " + maxC + " 分，当前选修已修 " + myCredit + " 分，本课后 " + (myCredit + courseCredit) + " 分");
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
        enr.setSemesterNo(GraduationService.currentSemesterNo(
                userRepo.findById(studentId).map(User::getGrade).orElse(null), java.time.LocalDate.now(),
                sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset()));
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
        // 历史学期不可退课：只有当前学期的修读记录才允许退（真实高校：学期结束成绩定档）
        int curSem = GraduationService.currentSemesterNo(
                userRepo.findById(studentId).map(User::getGrade).orElse(null), java.time.LocalDate.now(),
                sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset());
        Enrollment cur = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> courseId.equals(e.getCourseId())).findFirst().orElse(null);
        if (cur != null && cur.getSemesterNo() != null && cur.getSemesterNo() != curSem) {
            result.put("success", false);
            result.put("msg", "该课程属于已结束的学期，成绩已定档，不可退课");
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
        // 换课保底清理：退掉的是某条保底换课的源课 → 新课转为普通报名（保底失效，新课投分仍有效）
        clearSwapMarker(studentId, courseId, round);
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
        // 旧课若是某条保底换课的源课：保底失效，新课转为普通报名
        clearSwapMarker(studentId, oldCourseId, round);
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

    /**
     * 换课 Swap（原子互换，借鉴 Auto_courseGrabber）：已选课 A ↔ 新课 B，
     * 解决「退了才敢选、选不上就没退」的痛点——A 未被换掉前绝不先丢。
     * <ul>
     *   <li>第 1、2 轮（积分制）：登记「保底换课」——A 原样保留，新课 B 以 pending 报名并标记
     *       swapFrom=A；本轮结算时 B 被录取则自动退掉 A 完成互换，B 未录取则退分、A 纹丝不动。
     *       投分上限放宽为「剩余积分 + A 已投积分」（换课成功即释放 A 的积分）。</li>
     *   <li>第 3 轮（先到先得）：当场原子互换——B 有空位且各项校验（剔除 A 口径）全部通过时，
     *       同一事务内退 A + 录 B；任一不过则整单不动。</li>
     * </ul>
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> swap(Long studentId, Long fromCourseId, Long toCourseId, int points) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (studentId == null || fromCourseId == null || toCourseId == null) {
            result.put("success", false); result.put("msg", "参数不完整"); return result;
        }
        SystemConfig sys = getSystemConfig();
        int round = sys.getCurrentRound();
        if (!sys.getRoundOpen()) { result.put("success", false); result.put("msg", "当前轮次未开放，无法换课"); return result; }
        if (fromCourseId.equals(toCourseId)) { result.put("success", false); result.put("msg", "新课不能与原课程相同"); return result; }

        User stu = userRepo.findByIdForUpdate(studentId).orElse(null);
        if (stu == null) { result.put("success", false); result.put("msg", "用户不存在，请重新登录"); return result; }
        if (!"student".equals(stu.getRole())) { result.put("success", false); result.put("msg", "仅学生可以换课"); return result; }
        // 错峰开放校验（与报名同口径）
        if (round < 3) {
            String gateMsg = staggerService.checkGate(round, stu.getGrade());
            if (gateMsg != null) { result.put("success", false); result.put("msg", gateMsg); return result; }
        }

        // 按课程 ID 升序加锁，避免两个学生互相换课时形成 A→B / B→A 的死锁（与改选相同）
        Long firstId = Math.min(fromCourseId, toCourseId);
        Long secondId = Math.max(fromCourseId, toCourseId);
        Course first = courseRepo.findByIdForUpdate(firstId).orElse(null);
        Course second = courseRepo.findByIdForUpdate(secondId).orElse(null);
        Course from = fromCourseId.equals(firstId) ? first : second;
        Course to = toCourseId.equals(firstId) ? first : second;

        if (from == null) { result.put("success", false); result.put("msg", "原课程不存在"); return result; }
        if (isRequired(from)) { result.put("success", false); result.put("msg", "必修课由教学计划自动安排，不能换课"); return result; }
        if (to == null) { result.put("success", false); result.put("msg", "新课程不存在"); return result; }
        if (isRequired(to)) { result.put("success", false); result.put("msg", "必修课由教学计划自动安排，无需换课"); return result; }

        // 原课必须是本轮进行中的报名（pending / approved）
        Enrollment old = getEnrollment(studentId, fromCourseId, round);
        if (old == null || (!"pending".equals(old.getStatus()) && !"approved".equals(old.getStatus()))) {
            result.put("success", false); result.put("msg", "《" + from.getName() + "》不在进行中的报名里，无法作为换课保底"); return result;
        }
        // 已结束学期的课程成绩定档，与退课同口径，不可换出
        int mySem = GraduationService.currentSemesterNo(stu.getGrade(), java.time.LocalDate.now(),
                sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset());
        if (old.getSemesterNo() != null && old.getSemesterNo() != mySem) {
            result.put("success", false); result.put("msg", "《" + from.getName() + "》属于已结束的学期，成绩已定档，不可换课"); return result;
        }
        // 链式换课防护：原课自身已挂着换课标记（它是另一条换课的新课）→ 禁止
        if (old.getSwapFromCourseId() != null) {
            result.put("success", false); result.put("msg", "《" + from.getName() + "》已挂有换课保底，请先取消原换课"); return result;
        }

        // 新课：不能已在报名中
        Enrollment existNew = getEnrollment(studentId, toCourseId, round);
        // 仅本学期的在选记录才算「已报名」；历史学期存量记录放行——下方创建时会复用该记录并归位学期号
        if (existNew != null && !"rejected".equals(existNew.getStatus()) && !"dropped".equals(existNew.getStatus())
                && (existNew.getSemesterNo() == null || existNew.getSemesterNo() == mySem)) {
            result.put("success", false); result.put("msg", "你已报名《" + to.getName() + "》，无需换课"); return result;
        }
        // 一门原课同时只能挂一个换课保底
        for (Enrollment e : enrollmentRepo.findByStudentIdAndRound(studentId, round)) {
            if ("dropped".equals(e.getStatus()) || "rejected".equals(e.getStatus())) continue;
            if (fromCourseId.equals(e.getSwapFromCourseId()) && !e.getCourseId().equals(toCourseId)) {
                result.put("success", false); result.put("msg", "《" + from.getName() + "》已挂有换课保底，请先取消原换课"); return result;
            }
        }

        // 新课先修课 / 已通过校验（与报名同口径）
        if (passedCourseIds(studentId).contains(toCourseId)) {
            result.put("success", false); result.put("msg", "该课程已通过，无需重修"); return result;
        }
        if (to.getPrereqId() != null) {
            Grade preGrade = gradeRepo.findByStudentIdAndCourseId(studentId, to.getPrereqId()).orElse(null);
            if (!GradeService.passed(preGrade)) {
                Course pre = courseRepo.findById(to.getPrereqId()).orElse(null);
                result.put("success", false);
                result.put("msg", "需先通过《" + (pre != null ? pre.getName() : "先修课程") + "》才能换到《" + to.getName() + "》");
                return result;
            }
        }

        // ===== 新课校验：时间冲突（剔除保底课 A——换课前后 A 让位给 B，二者冲突是换课常态） =====
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            if (e.getSemesterNo() == null || e.getSemesterNo() != mySem) continue;
            if (fromCourseId.equals(e.getCourseId())) continue;   // 保底课剔除
            Course ec = courseRepo.findById(e.getCourseId()).orElse(null);
            if (ec != null && hasTimeConflict(to, ec)) {
                result.put("success", false); result.put("msg", "与已录取课程《" + ec.getName() + "》时间冲突"); return result;
            }
        }

        // 每学期门数上限快检：已录取选修（剔除 A）+ 1 门新课是否会超（第 1/2 轮结算仍会终审）
        Integer maxCourses = sys.getMaxCoursesPerSemester();
        if (maxCourses != null && maxCourses > 0 && approvedElectiveCount(studentId, fromCourseId) >= maxCourses) {
            result.put("success", false);
            result.put("msg", "换课后本学期选修将达到 " + (approvedElectiveCount(studentId, fromCourseId) + 1)
                    + " 门，超出上限 " + maxCourses + " 门，请先退掉其他课程");
            return result;
        }

        // 每学期学分上限快检（口径与报名一致：剔除 A 的已修学分 + 新课学分）
        int myCredit = enrollmentRepo.findByStudentId(studentId).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .filter(e -> e.getSemesterNo() == null || e.getSemesterNo() == mySem)
                .filter(e -> !fromCourseId.equals(e.getCourseId()))
                .map(e -> courseRepo.findById(e.getCourseId()).orElse(null))
                .filter(ec -> ec != null && !CourseGroup.isRequired(ec.getCategory()))
                .mapToInt(ec -> ec.getCredit() == null ? 0 : ec.getCredit()).sum();
        int toCredit = to.getCredit() == null ? 0 : to.getCredit();
        Integer maxC = sys.getMaxCreditPerSemester();
        if (maxC != null && maxC > 0 && myCredit + toCredit > maxC) {
            result.put("success", false);
            result.put("msg", "换课后超出本学期选修学分上限 " + maxC + " 分（当前 " + myCredit + " 分 + 本课 " + toCredit + " 分）");
            return result;
        }

        boolean firstCome = round >= 3;
        if (firstCome) {
            // ===== 第 3 轮：当场原子互换 =====
            // 毕业要求封顶（剔除 A 口径），防止换课成为超修后门
            String group = CourseGroup.groupOf(to.getCategory());
            int groupReq = gradReqOf(group, sys);
            if (groupReq > 0) {
                Map<String, Integer> groupEarned = gradGroupEarned(studentId, null, null);
                if ("approved".equals(old.getStatus())) {
                    Course fc = from;
                    if (fc != null && !failedCourseIds(studentId).contains(fromCourseId)) {
                        groupEarned.merge(CourseGroup.groupOf(fc.getCategory()),
                                -(fc.getCredit() == null ? 0 : fc.getCredit()), Integer::sum);
                    }
                }
                int used = groupEarned.getOrDefault(group, 0);
                if (used + toCredit > groupReq) {
                    result.put("success", false);
                    result.put("msg", "「" + group + "」类毕业学分换课后将达 " + (used + toCredit)
                            + " 分，超出上限 " + groupReq + " 分");
                    return result;
                }
            }
            int approvedCnt = (int) enrollmentRepo.findByCourseIdAndRound(toCourseId, round).stream()
                    .filter(e -> "approved".equals(e.getStatus())).count();
            int cap = to.getCapacity() == null ? 0 : to.getCapacity();
            if (approvedCnt >= cap) {
                result.put("success", false);
                result.put("msg", "本轮为补选，《" + to.getName() + "》名额已满，无法换入（原课未动）");
                return result;
            }
            // 全部通过 → 同一事务内退 A + 录 B；任一步失败事务回滚，A 不会丢
            old.setStatus("dropped");
            enrollmentRepo.save(old);
            clearSwapMarker(studentId, fromCourseId, round);
            Enrollment enr = existNew;
            if (enr == null) {
                enr = new Enrollment();
                enr.setStudentId(studentId);
                enr.setCourseId(toCourseId);
                enr.setRound(round);
            }
            enr.setPoints(0);
            enr.setStatus("approved");
            enr.setSemesterNo(mySem);
            enr.setSwapFromCourseId(null);
            enrollmentRepo.save(enr);
            // 释放 A 的名额并递补候补队列
            waitlistService.promoteIfNeeded(fromCourseId, round);
            notifySwap(studentId, from, to, true,
                    "已当场退掉《" + from.getName() + "》并录取《" + to.getName() + "》");
            result.put("success", true);
            result.put("msg", "换课成功！已退掉《" + from.getName() + "》，换入《" + to.getName() + "》");
            return result;
        }

        // ===== 第 1、2 轮：登记保底换课（A 保留，B pending + swapFrom 标记，结算时裁决） =====
        int remaining = getRemainingPoints(studentId, round);
        int oldPoints = old.getPoints() == null ? 0 : old.getPoints();
        if (points < 0) { result.put("success", false); result.put("msg", "投入积分不能为负数"); return result; }
        if (points > POINTS_PER_ROUND) { result.put("success", false); result.put("msg", "单门课程最多投入 " + POINTS_PER_ROUND + " 分"); return result; }
        if (points > remaining + oldPoints) {
            result.put("success", false);
            result.put("msg", "换课投分上限为 剩余积分 " + remaining + " + 《" + from.getName() + "》已投 " + oldPoints
                    + " = " + (remaining + oldPoints) + " 分（换课成功即释放原课积分）");
            return result;
        }
        Enrollment enr = existNew;
        if (enr == null) {
            enr = new Enrollment();
            enr.setStudentId(studentId);
            enr.setCourseId(toCourseId);
            enr.setRound(round);
        }
        enr.setPoints(points);
        enr.setStatus("pending");
        enr.setSemesterNo(mySem);
        enr.setSwapFromCourseId(fromCourseId);
        enrollmentRepo.save(enr);
        try {
            notificationService.notify(studentId, "enroll", "🔄 换课保底已挂起",
                    "《" + from.getName() + "》→《" + to.getName() + "》" + (points > 0 ? "（投入 " + points + " 分）" : "")
                            + "：本轮结算时若《" + to.getName() + "》录取将自动退掉《" + from.getName() + "》；"
                            + "若未录取则《" + from.getName() + "》原样保留、投分退还",
                    to.getId());
        } catch (Exception ignore) {}
        result.put("success", true);
        result.put("msg", "换课保底已挂起：《" + from.getName() + "》→《" + to.getName() + "》"
                + (points > 0 ? "（投入 " + points + " 分）" : "") + "。结算时《" + to.getName()
                + "》录取则自动退掉《" + from.getName() + "》，未录取则《" + from.getName() + "》原样保留");
        return result;
    }

    /** 退掉某门课作为换课源课时，把挂在它上面的保底标记清掉（新课转为普通报名） */
    private void clearSwapMarker(Long studentId, Long fromCourseId, int round) {
        for (Enrollment e : enrollmentRepo.findByStudentIdAndRound(studentId, round)) {
            if (fromCourseId.equals(e.getSwapFromCourseId())) {
                e.setSwapFromCourseId(null);
                enrollmentRepo.save(e);
            }
        }
    }

    /** 已录取选修课程数（按课程去重、剔除指定课程，挂科未重修的不计）——换课门数上限快检用 */
    private int approvedElectiveCount(Long studentId, Long excludeCourseId) {
        int round = getSystemConfig().getCurrentRound();
        // 本学期口径：换课是同学期等量替换，历史学期已结课的选修不占本学期门数
        int mySem = GraduationService.currentSemesterNo(
                userRepo.findById(studentId).map(User::getGrade).orElse(null), java.time.LocalDate.now(),
                getSystemConfig().getSemesterOffset() == null ? 0 : getSystemConfig().getSemesterOffset());
        Set<Long> failed = failedCourseIds(studentId);
        Set<Long> retakingNow = enrollmentRepo.findByStudentIdAndRound(studentId, round).stream()
                .filter(e -> "approved".equals(e.getStatus()))
                .map(Enrollment::getCourseId)
                .filter(failed::contains)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> seen = new LinkedHashSet<>();
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            if (e.getSemesterNo() == null || e.getSemesterNo() != mySem) continue;   // 本学期口径
            if (e.getCourseId().equals(excludeCourseId)) continue;
            if (failed.contains(e.getCourseId()) && !retakingNow.contains(e.getCourseId())) continue;
            Course ec = courseRepo.findById(e.getCourseId()).orElse(null);
            if (ec == null || !CourseGroup.isRequired(ec.getCategory())) seen.add(e.getCourseId());
        }
        return seen.size();
    }

    /** 换课结果站内推送 */
    private void notifySwap(Long studentId, Course from, Course to, boolean ok, String detail) {
        try {
            notificationService.notify(studentId, "enroll",
                    ok ? "🔄 换课成功" : "🔄 换课结果",
                    "《" + from.getName() + "》→《" + to.getName() + "》：" + detail,
                    to.getId());
        } catch (Exception ignore) {}
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
        final int roundNo = round;   // lambda 内需要实际最终变量（round 可能被上面的兜底赋值）

        Map<Long, Course> courseById = new LinkedHashMap<>();
        for (Course c : courseRepo.findAll()) courseById.put(c.getId(), c);

        List<Enrollment> all = enrollmentRepo.findByRound(round);

        // 已录取课程（含必修课）按学生归档，用于冲突检测与学分累计。
        // 跨轮次统计：轮次是同一学期内的选课批次，之前轮次录取的课同样占用本学期课表与学分额度；
        // creditByStudent 只累计非必修学分：必修学分由教学计划自动安排，不占选修学分上限。
        Map<Long, Map<Long, Course>> approvedByStudent = new HashMap<>();
        Map<Long, Integer> creditByStudent = new HashMap<>();
        // 各学生在“毕业大类”（必修/选修/通识/实践）上已修的学分，懒加载并缓存。
        // 学分封顶不能只看本学期，毕业要求是跨学期的总量，所以这里要统计该学生全部已录取课程。
        Map<Long, Map<String, Integer>> groupEarnedByStudent = new HashMap<>();
        // 各学生当前学期序缓存（修读记录按学期归属，本学期冲突/学分/门数只看本学期）
        Map<Long, Integer> curSemByStudent = new HashMap<>();
        for (Enrollment e : enrollmentRepo.findAll()) {
            if (!"approved".equals(e.getStatus())) continue;
            Course c = courseById.get(e.getCourseId());
            if (c == null) continue;
            int sem = curSemByStudent.computeIfAbsent(e.getStudentId(), sid -> {
                User u = userRepo.findById(sid).orElse(null);
                return GraduationService.currentSemesterNo(u == null ? null : u.getGrade(), java.time.LocalDate.now(),
                        sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset());
            });
            if (e.getSemesterNo() != null && e.getSemesterNo() != sem) continue;   // 历史学期记录不占本学期
            Map<Long, Course> approved = approvedByStudent.computeIfAbsent(e.getStudentId(), k -> new LinkedHashMap<>());
            if (approved.putIfAbsent(c.getId(), c) == null) {
                if (!CourseGroup.isRequired(c.getCategory())) {
                    creditByStudent.merge(e.getStudentId(), nz(c.getCredit()), Integer::sum);
                }
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
        // 每生跨轮次已录取课程集合（按课程去重），用于门数上限裁决；懒加载同 gradGroupEarned 的缓存思路。
        // 必修课不占门数额度，这里只收非必修课程。
        // 挂科课程不占额度（允许重修或改选新课）；本轮正在重修的挂科课仍计入门数。
        Map<Long, Set<Long>> courseSetByStudent = new HashMap<>();
        Map<Long, Set<Long>> passedByStudent = new HashMap<>();
        Set<String> processed = new HashSet<>();
        // 本结算内刚录取的报名 id：换课成交回退源课录取计数时，用于区分「本结算刚录取」与「往轮已录取」
        Set<Long> admittedNow = new HashSet<>();
        int admittedCount = 0, rejectedCount = 0;

        for (Enrollment e : candidates) {
            // 换课成交时源课会被自动退掉（可能就发生在本循环里）：已退课的候选直接跳过
            if ("dropped".equals(e.getStatus())) continue;
            Course c = courseById.get(e.getCourseId());
            Long cid = c.getId();
            Long sid = e.getStudentId();
            applied.merge(cid, 1, Integer::sum);

            Map<Long, Course> approved = approvedByStudent.computeIfAbsent(sid, k -> new LinkedHashMap<>());
            Map<String, Integer> groupEarned = gradGroupEarned(sid, courseById, groupEarnedByStudent);
            Set<Long> approvedCourseSet = courseSetByStudent.computeIfAbsent(sid, k -> {
                Set<Long> s = new HashSet<>();
                Set<Long> failed = failedCourseIds(k);
                Set<Long> retakingNow = new HashSet<>();
                int mySem = curSemByStudent.getOrDefault(k, GraduationService.currentSemesterNo(
                        userRepo.findById(k).map(User::getGrade).orElse(null), java.time.LocalDate.now(),
                        sys.getSemesterOffset() == null ? 0 : sys.getSemesterOffset()));
                for (Enrollment x : enrollmentRepo.findByStudentIdAndRound(k, roundNo)) {
                    if ("approved".equals(x.getStatus()) && failed.contains(x.getCourseId())) retakingNow.add(x.getCourseId());
                }
                for (Enrollment x : enrollmentRepo.findByStudentId(k)) {
                    if (!"approved".equals(x.getStatus())) continue;
                    if (x.getSemesterNo() != null && x.getSemesterNo() != mySem) continue;   // 只看本学期
                    Course xc = courseById.get(x.getCourseId());
                    if (xc == null || CourseGroup.isRequired(xc.getCategory())) continue; // 必修不占门数额度
                    if (failed.contains(x.getCourseId()) && !retakingNow.contains(x.getCourseId())) continue;
                    s.add(x.getCourseId());
                }
                return s;
            });
            Set<Long> passedSet = passedByStudent.computeIfAbsent(sid, k -> passedCourseIds(k));

            // ===== 换课保底（Swap）：把源课 A 从各项口径中剔除后再裁决新课 B =====
            Long swapFromId = e.getSwapFromCourseId();
            Enrollment swapSrc = null;
            if (swapFromId != null) {
                swapSrc = enrollmentRepo.findByStudentIdAndCourseIdAndRound(sid, swapFromId, roundNo).orElse(null);
                if (swapSrc == null || (!"pending".equals(swapSrc.getStatus()) && !"approved".equals(swapSrc.getStatus()))) {
                    swapSrc = null;                       // 保底课已退/已落选：退化为普通报名
                    e.setSwapFromCourseId(null);
                }
            }
            Map<Long, Course> effApproved = approved;
            Set<Long> effCourseSet = approvedCourseSet;
            Map<String, Integer> effGroup = groupEarned;
            int effCredit = creditByStudent.getOrDefault(sid, 0);
            if (swapSrc != null) {
                effApproved = new LinkedHashMap<>(approved);
                effApproved.remove(swapFromId);
                effCourseSet = new HashSet<>(approvedCourseSet);
                effCourseSet.remove(swapFromId);
                effGroup = new LinkedHashMap<>(groupEarned);
                Course srcCourse = courseById.get(swapFromId);
                if ("approved".equals(swapSrc.getStatus()) && srcCourse != null
                        && !CourseGroup.isRequired(srcCourse.getCategory())) {
                    int srcCredit = nz(srcCourse.getCredit());
                    effCredit = Math.max(0, effCredit - srcCredit);
                    if (!failedCourseIds(sid).contains(swapFromId)) {
                        effGroup.merge(CourseGroup.groupOf(srcCourse.getCategory()), -srcCredit, Integer::sum);
                    }
                }
            }

            String reason = settleRejectReason(e, c, effApproved, effCourseSet, passedSet, processed, admitted,
                    sys, effCredit, effGroup);

            if (reason == null) {
                e.setStatus("approved");
                admitted.merge(cid, 1, Integer::sum);
                cutline.put(cid, nz(e.getPoints()));
                approved.put(cid, c);
                approvedCourseSet.add(cid);
                creditByStudent.merge(sid, nz(c.getCredit()), Integer::sum);
                // 本次录取的学分也要计入该大类，后续候选课才能正确判断是否毕业超修
                groupEarned.merge(CourseGroup.groupOf(c.getCategory()), nz(c.getCredit()), Integer::sum);
                admittedCount++;
                admittedNow.add(e.getId());
                if (swapSrc != null) {
                    // 换课成交：新课 B 已录取 → 自动退掉源课 A（含各项口径回退与候补递补）
                    if (executeSwapAtSettlement(swapSrc, courseById, approvedByStudent, creditByStudent,
                            groupEarnedByStudent, courseSetByStudent, admitted, admittedNow)) {
                        admittedCount--;   // 源课是本结算刚录取的：回退其录取计数，让出的名额可被后续候选使用
                    }
                    notifySwap(sid, courseById.get(swapFromId), c, true,
                            "换课成交：已自动退掉《" + (courseById.get(swapFromId) != null ? courseById.get(swapFromId).getName() : "原课程")
                                    + "》并录取《" + c.getName() + "》");
                } else {
                    notifyResult(e, c, true, null);
                }
            } else {
                e.setStatus("rejected");
                rejectedCount++;
                rejectReasons.computeIfAbsent(cid, k -> new LinkedHashMap<>()).merge(reason, 1, Integer::sum);
                if (swapSrc != null) {
                    // 换课未成交：B 退分（rejected 自动不计），保底课 A 原样保留——这正是「选不上就没退」的解药
                    Course srcCourse = courseById.get(swapFromId);
                    notifySwap(sid, srcCourse, c, false,
                            "《" + c.getName() + "》未被录取（" + reason + "），保底课《"
                                    + (srcCourse != null ? srcCourse.getName() : "原课程") + "》已原样保留，投分已退还");
                } else {
                    notifyResult(e, c, false, reason);
                }
            }
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
     * 判断顺序与错误提示保持一致：重复 → 已通过 → 容量 → 时间冲突 → 门数上限 → 学分上限 → 毕业要求。
     * 重复报名只按当轮候选去重（同轮同课有唯一约束）；跨轮重复是合法重修，交给「已通过」与冲突/额度检查裁决。
     */
    private String settleRejectReason(Enrollment e, Course c, Map<Long, Course> approved,
                                      Set<Long> approvedCourseSet, Set<Long> passedSet, Set<String> processed,
                                      Map<Long, Integer> admitted,
                                      SystemConfig sys, int creditUsed,
                                      Map<String, Integer> groupEarned) {
        if (!processed.add(e.getStudentId() + ":" + c.getId())) {
            return "重复报名";
        }
        if (passedSet.contains(c.getId())) {
            return "该课程已通过，无需重修";
        }
        // 先修课校验：结算同样拦截未通过先修课的候选
        if (c.getPrereqId() != null && !passedSet.contains(c.getPrereqId())) {
            Course pre = courseRepo.findById(c.getPrereqId()).orElse(null);
            return "需先通过《" + (pre != null ? pre.getName() : "先修课程") + "》";
        }
        if (admitted.getOrDefault(c.getId(), 0) >= nz(c.getCapacity())) {
            return "容量已满";
        }
        if (hasTimeConflict(c, approved.values())) {
            return "与已录取课程时间冲突";
        }
        // 每学期门数上限：候选按积分全局降序处理，先到的高分课先占名额，超额者自然被拒。
        // approvedCourseSet 已只含非必修课程（必修不占门数额度）。
        Integer maxCourses = sys.getMaxCoursesPerSemester();
        if (maxCourses != null && maxCourses > 0 && approvedCourseSet.size() >= maxCourses) {
            return "本学期最多可选 " + maxCourses + " 门选修课，已选 " + approvedCourseSet.size() + " 门";
        }
        Integer maxC = sys.getMaxCreditPerSemester();
        if (maxC != null && maxC > 0
                && creditUsed + nz(c.getCredit()) > maxC) {
            return "超出本学期选修学分上限 " + maxC + " 分";
        }
        // 按毕业要求封顶：该大类已修学分 + 本课学分 > 毕业要求 → 拒绝（多修不算毕业学分，也不该占用名额）
        String group = CourseGroup.groupOf(c.getCategory());
        int groupReq = gradReqOf(group, sys);
        if (groupReq > 0 && groupEarned.getOrDefault(group, 0) + nz(c.getCredit()) > groupReq) {
            return "「" + group + "」类毕业学分已达上限 " + groupReq + " 分";
        }
        return null;
    }

    /**
     * 结算内执行换课成交：新课 B 已录取 → 自动退掉源课 A。
     * 同步回退 A 在各项统计口径中的贡献（已录取映射 / 门数集合 / 学分 / 毕业大类），
     * 并释放 A 的名额给候补队列。A 可能是往轮已录取，也可能是本结算刚录取——
     * 后者需由调用方回退录取计数（返回 true 表示「本结算刚录取」）。
     */
    private boolean executeSwapAtSettlement(Enrollment swapSrc, Map<Long, Course> courseById,
                                            Map<Long, Map<Long, Course>> approvedByStudent,
                                            Map<Long, Integer> creditByStudent,
                                            Map<Long, Map<String, Integer>> groupEarnedByStudent,
                                            Map<Long, Set<Long>> courseSetByStudent,
                                            Map<Long, Integer> admitted, Set<Long> admittedNow) {
        Long sid = swapSrc.getStudentId();
        Long srcCid = swapSrc.getCourseId();
        Course srcCourse = courseById.get(srcCid);
        boolean wasApproved = "approved".equals(swapSrc.getStatus());
        swapSrc.setStatus("dropped");
        enrollmentRepo.save(swapSrc);
        boolean admittedHere = admittedNow.remove(swapSrc.getId());
        if (admittedHere) {
            admitted.computeIfPresent(srcCid, (k, v) -> v > 0 ? v - 1 : 0);
        }
        if (wasApproved) {
            Map<Long, Course> ap = approvedByStudent.get(sid);
            if (ap != null) ap.remove(srcCid);
            Set<Long> cs = courseSetByStudent.get(sid);
            if (cs != null) cs.remove(srcCid);
            if (srcCourse != null && !CourseGroup.isRequired(srcCourse.getCategory())) {
                creditByStudent.merge(sid, -nz(srcCourse.getCredit()), Integer::sum);
                if (!failedCourseIds(sid).contains(srcCid)) {
                    Map<String, Integer> ge = groupEarnedByStudent.get(sid);
                    if (ge != null) {
                        ge.merge(CourseGroup.groupOf(srcCourse.getCategory()), -nz(srcCourse.getCredit()), Integer::sum);
                    }
                }
            }
        }
        // 源课让出的名额递补候补队列（锁课程行，与退课同口径）
        waitlistService.promoteIfNeeded(srcCid, swapSrc.getRound());
        return admittedHere;
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

    /** 学生已挂科的课程集合（有成绩记录且总评 < 60）：挂科不占毕业学分与门数额度，允许重修 */
    private Set<Long> failedCourseIds(Long studentId) {
        Set<Long> failed = new HashSet<>();
        for (Grade g : gradeRepo.findByStudentId(studentId)) {
            if (g.getTotalScore() != null && g.getTotalScore() < GradeService.PASS_LINE) failed.add(g.getCourseId());
        }
        return failed;
    }

    /** 学生已通过的课程集合（总评 ≥ 60）：通过的课不允许重复修读 */
    private Set<Long> passedCourseIds(Long studentId) {
        Set<Long> passed = new HashSet<>();
        for (Grade g : gradeRepo.findByStudentId(studentId)) {
            if (GradeService.passed(g)) passed.add(g.getCourseId());
        }
        return passed;
    }

    private Map<String, Integer> gradGroupEarnedUncached(Long studentId, Map<Long, Course> courseCache) {
        Map<String, Integer> earned = new LinkedHashMap<>();
        Set<Long> seenCourses = new HashSet<>();
        // 挂科的课程不算已修学分：否则重修会被「毕业学分已达上限」拦截，形成死锁
        Set<Long> failed = failedCourseIds(studentId);
        for (Enrollment e : enrollmentRepo.findByStudentId(studentId)) {
            if (!"approved".equals(e.getStatus())) continue;
            if (!seenCourses.add(e.getCourseId())) continue;   // 同一门课多轮都录取过只算一次学分
            if (failed.contains(e.getCourseId())) continue;    // 挂科重修中：不占毕业额度
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

    // 时间冲突检测（IntentService 预检也复用此口径）
    public boolean hasTimeConflict(Course a, Course b) {
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
