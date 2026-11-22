package com.zhixue.service;

import com.zhixue.entity.Course;
import com.zhixue.entity.CourseIntent;
import com.zhixue.entity.CourseReview;
import com.zhixue.entity.Enrollment;
import com.zhixue.entity.Grade;
import com.zhixue.entity.GradeRecord;
import com.zhixue.entity.OperationLog;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.entity.Waitlist;
import com.zhixue.repository.CourseIntentRepository;
import com.zhixue.repository.CourseRepository;
import com.zhixue.repository.CourseReviewRepository;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.repository.GradeRepository;
import com.zhixue.repository.OperationLogRepository;
import com.zhixue.repository.SystemConfigRepository;
import com.zhixue.repository.UserRepository;
import com.zhixue.repository.WaitlistRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 管理端统计报表：课程热度 / 轮次趋势 / 满员率 / 教师工作量 */
@Service
public class StatsService {

    @Autowired private EnrollmentRepository enrollmentRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private WaitlistRepository waitlistRepo;
    @Autowired private CourseReviewRepository reviewRepo;
    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private CourseService courseService;
    @Autowired private GradeRepository gradeRepo;
    @Autowired private GradeLedgerService ledger;
    @Autowired private CourseIntentRepository intentRepo;
    @Autowired private OperationLogRepository logRepo;

    private static final Pattern COURSE_TIME =
            Pattern.compile("(周[一二三四五六日])\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})");
    private static final String[] DAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final String[] PERIOD_LABELS = {"08:00", "10:00", "14:00", "16:00", "19:00"};
    private static final String[] PERIOD_RANGES = {"08:00-09:40", "10:00-11:40", "14:00-15:40", "16:00-17:40", "19:00-20:40"};
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_SEC = DateTimeFormatter.ofPattern("HH:mm:ss");

    private int nz(Integer v) { return v == null ? 0 : v; }
    private double pct(int a, int b) { return b <= 0 ? 0.0 : Math.round(a * 1000.0 / b) / 10.0; }

    public Map<String, Object> report() {
        com.zhixue.entity.SystemConfig cfg = sysRepo.findById(1L).orElse(null);
        int round = cfg == null || cfg.getCurrentRound() == null ? 1 : cfg.getCurrentRound();
        List<Course> courses = courseRepo.findAll();
        List<Enrollment> all = enrollmentRepo.findAll();

        // ===== 单次遍历预聚合 =====
        // 逐课对全量选课做流式过滤在数据量增长后不可行：4025 门课 × 31 万条选课 ≈ 12 亿次比较，
        // 曾把 /stats/report 拖到分钟级超时（前端 30s 断开 → 报表卡片全空）。
        // 现在一遍分组：按课程（本轮未退课 → 热度/满员率）、按课程（本轮已录取 → 教师工作量）、按轮次（趋势计数）。
        Map<Long, List<Enrollment>> byCourseRound = new HashMap<>();
        Map<Long, Integer> approvedByCourse = new HashMap<>();
        Map<Integer, int[]> roundStats = new HashMap<>();   // round -> [applied, approved, rejected, dropped]
        for (Enrollment e : all) {
            Integer r = e.getRound();
            if (r == null) continue;
            String status = e.getStatus();
            boolean dropped = "dropped".equals(status);
            boolean approved = "approved".equals(status);
            int[] st = roundStats.computeIfAbsent(r, k -> new int[4]);
            if (!dropped) st[0]++;
            if (approved) st[1]++;
            if ("rejected".equals(status)) st[2]++;
            if (dropped) st[3]++;
            if (r != round) continue;
            if (!dropped) byCourseRound.computeIfAbsent(e.getCourseId(), k -> new ArrayList<>()).add(e);
            if (approved) approvedByCourse.merge(e.getCourseId(), 1, Integer::sum);
        }

        // 候补一次拉全按课程计数，替代逐课查询
        Map<Long, Integer> waitByCourse = new HashMap<>();
        for (Waitlist w : waitlistRepo.findByRound(round)) {
            if (!WaitlistService.ACTIVE.contains(w.getStatus())) continue;
            waitByCourse.merge(w.getCourseId(), 1, Integer::sum);
        }

        // 教师名 / 课程评价一次拉全，替代逐课 findById / findByCourseId 的 N+1
        Map<Long, String> nameMap = courseService.userNameMap();
        Map<Long, int[]> reviewByCourse = new HashMap<>();   // courseId -> [评价数, 评分总和]
        for (CourseReview rev : reviewRepo.findAll()) {
            int[] rv = reviewByCourse.computeIfAbsent(rev.getCourseId(), k -> new int[2]);
            rv[0]++;
            rv[1] += nz(rev.getRating());
        }

        // 课程热度 + 满员率（本轮，未退课）
        List<Map<String, Object>> hot = new ArrayList<>();
        List<Map<String, Object>> fill = new ArrayList<>();
        int capSum = 0, approvedSum = 0, appliedSum = 0, fullCount = 0, waitSum = 0;
        for (Course c : courses) {
            boolean req = c.getCategory() != null && c.getCategory().contains("必修");
            List<Enrollment> ce = byCourseRound.getOrDefault(c.getId(), List.of());
            int applied = ce.size();
            int approved = (int) ce.stream().filter(e -> "approved".equals(e.getStatus())).count();
            int cap = nz(c.getCapacity());
            int wait = waitByCourse.getOrDefault(c.getId(), 0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", nameMap.getOrDefault(c.getTeacherId(), "未分配"));
            m.put("capacity", cap);
            m.put("applied", applied);
            m.put("approved", approved);
            m.put("waiting", wait);
            m.put("fillRate", pct(approved, cap));
            m.put("required", req);
            hot.add(m);
            fill.add(m);
            // 全校概览只统计本轮有报名的课程（毕业学期多为必修课，口径随实际开课走，
            // 避免出现"全校 650 门容量 vs 17 条选修报名"造成的 0.1% 假满员率）
            if (applied > 0) {
                capSum += cap; approvedSum += approved; appliedSum += applied; waitSum += wait;
                if (cap > 0 && approved >= cap) fullCount++;
            }
        }
        hot.sort((a, b) -> Integer.compare((int) b.get("applied"), (int) a.get("applied")));
        fill.sort((a, b) -> Double.compare((double) b.get("fillRate"), (double) a.get("fillRate")));

        // 轮次报名趋势
        int maxRound = roundStats.keySet().stream().max(Integer::compare).orElse(round);
        maxRound = Math.max(maxRound, round);
        List<Map<String, Object>> rounds = new ArrayList<>();
        for (int r = 1; r <= maxRound; r++) {
            int[] st = roundStats.getOrDefault(r, new int[4]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("round", r);
            m.put("applied", st[0]);
            m.put("approved", st[1]);
            m.put("rejected", st[2]);
            m.put("dropped", st[3]);
            rounds.add(m);
        }

        // 教师工作量
        List<Map<String, Object>> teachers = new ArrayList<>();
        for (User u : userRepo.findAll()) {
            if (!"teacher".equals(u.getRole())) continue;
            List<Course> tc = courseService.findByTeacher(u.getId());
            int students = 0, rc = 0, rs = 0;
            for (Course c : tc) {
                students += approvedByCourse.getOrDefault(c.getId(), 0);
                int[] rv = reviewByCourse.getOrDefault(c.getId(), new int[2]);
                rs += rv[1];
                rc += rv[0];
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teacherId", u.getId());
            m.put("teacherName", u.getName());
            m.put("college", u.getCollege());
            m.put("courseCount", tc.size());
            m.put("studentCount", students);
            m.put("reviewCount", rc);
            m.put("avgRating", rc == 0 ? 0.0 : Math.round((double) rs / rc * 10) / 10.0);
            teachers.add(m);
        }
        teachers.sort((a, b) -> Integer.compare((int) b.get("studentCount"), (int) a.get("studentCount")));

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("round", round);
        // 轮次开关状态随报表下发：前端头部徽标按真实状态显示"进行中/未开启"，不再写死
        overview.put("roundOpen", cfg != null && Boolean.TRUE.equals(cfg.getRoundOpen()));
        overview.put("totalCourses", courses.size());
        overview.put("totalUsers", userRepo.count());
        overview.put("applied", appliedSum);
        overview.put("approved", approvedSum);
        overview.put("fillRate", pct(approvedSum, capSum));
        overview.put("fullCount", fullCount);
        overview.put("waiting", waitSum);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overview", overview);
        out.put("hot", hot);
        out.put("fill", fill);
        out.put("rounds", rounds);
        out.put("teachers", teachers);
        return out;
    }

    /* ============================================================
       选课分析中心
       ============================================================ */

    private int currentRound() {
        return sysRepo.findById(1L).map(c -> c.getCurrentRound() == null ? 1 : c.getCurrentRound()).orElse(1);
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /** 线性插值分位数，用于箱线图的 Q1 / 中位数 / Q3 */
    private double quantile(double[] sorted, double p) {
        if (sorted.length == 0) return 0;
        if (sorted.length == 1) return sorted[0];
        double pos = (sorted.length - 1) * p;
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sorted[lo];
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }

    /** 积分分析的公共聚合：按课程分组、算录取线与分数箱线统计 */
    public Map<String, Object> pointsAnalysis(Integer round) {
        int r = (round == null || round < 1) ? currentRound() : round;
        List<Course> courses = courseRepo.findAll();

        Map<Long, List<Enrollment>> grouped = new LinkedHashMap<>();
        for (Enrollment e : enrollmentRepo.findByRound(r)) {
            if ("dropped".equals(e.getStatus())) continue;
            grouped.computeIfAbsent(e.getCourseId(), k -> new ArrayList<>()).add(e);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int electiveCount = 0, zeroApplied = 0;
        int totalApplied = 0, totalApproved = 0;
        int tieRejected = 0, highRejected = 0;
        int cutCount = 0, pointCount = 0, maxPoint = 0;
        double cutSum = 0, pointSum = 0;

        for (Course c : courses) {
            // 积分只在非必修课中竞争；必修课由教学计划自动安排
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            electiveCount++;
            List<Enrollment> es = grouped.get(c.getId());
            if (es == null || es.isEmpty()) { zeroApplied++; continue; }

            List<Enrollment> approved = es.stream().filter(e -> "approved".equals(e.getStatus())).toList();
            List<Enrollment> rejected = es.stream().filter(e -> "rejected".equals(e.getStatus())).toList();
            int applied = es.size();
            totalApplied += applied;
            totalApproved += approved.size();

            Integer cutline = null;
            if (!approved.isEmpty() && !rejected.isEmpty()) {
                cutline = approved.stream().mapToInt(e -> nz(e.getPoints())).min().orElse(0);
                cutSum += cutline;
                cutCount++;
            }

            int tRej = 0, hRej = 0;
            if (cutline != null) {
                for (Enrollment e : rejected) {
                    int p = nz(e.getPoints());
                    if (p > cutline) hRej++;             // 分数高于录取线仍落选：通常是时间/学分冲突
                    else if (p == cutline) tRej++;       // 压线同分抽签落选
                }
            }
            tieRejected += tRej;
            highRejected += hRej;

            List<Enrollment> sorted = new ArrayList<>(es);
            sorted.sort(Comparator.comparingInt(e -> nz(e.getPoints())));
            double[] pts = sorted.stream().mapToDouble(e -> nz(e.getPoints())).toArray();
            for (double p : pts) {
                pointSum += p;
                pointCount++;
                maxPoint = Math.max(maxPoint, (int) p);
            }

            // 单课程最多采样 160 个点，避免热门课拖慢接口
            int step = Math.max(1, (int) Math.ceil(sorted.size() / 160.0));
            List<Map<String, Object>> pointList = new ArrayList<>();
            for (int i = 0; i < sorted.size(); i += step) {
                Enrollment e = sorted.get(i);
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("p", nz(e.getPoints()));
                pm.put("s", e.getStatus());
                pointList.add(pm);
            }

            Map<String, Object> box = new LinkedHashMap<>();
            box.put("min", pts.length == 0 ? 0 : pts[0]);
            box.put("q1", round1(quantile(pts, .25)));
            box.put("median", round1(quantile(pts, .5)));
            box.put("q3", round1(quantile(pts, .75)));
            box.put("max", pts.length == 0 ? 0 : pts[pts.length - 1]);
            box.put("avg", round1(Arrays.stream(pts).average().orElse(0)));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("category", c.getCategory());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("capacity", nz(c.getCapacity()));
            m.put("applied", applied);
            m.put("approved", approved.size());
            m.put("rejected", rejected.size());
            m.put("cutline", cutline);
            m.put("fillRate", pct(approved.size(), nz(c.getCapacity())));
            m.put("box", box);
            m.put("tieRejected", tRej);
            m.put("highRejected", hRej);
            m.put("points", pointList);
            rows.add(m);
        }

        rows.sort((a, b) -> {
            int c = Integer.compare((int) b.get("applied"), (int) a.get("applied"));
            return c != 0 ? c : Long.compare((long) a.get("courseId"), (long) b.get("courseId"));
        });

        Map<String, Object> hottest = rows.isEmpty() ? null : rows.get(0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", r);
        summary.put("electiveCourses", electiveCount);
        summary.put("activeCourses", rows.size());
        summary.put("zeroApplied", zeroApplied);
        summary.put("applied", totalApplied);
        summary.put("approved", totalApproved);
        summary.put("avgCutline", cutCount == 0 ? 0.0 : round1(cutSum / cutCount));
        summary.put("tieRejected", tieRejected);
        summary.put("highRejected", highRejected);
        summary.put("avgPoints", pointCount == 0 ? 0.0 : round1(pointSum / pointCount));
        summary.put("maxPoints", Math.max(50, maxPoint));
        summary.put("competition", totalApproved == 0 ? 0.0 : round1((double) totalApplied / totalApproved));
        summary.put("hottestCourse", hottest == null ? null : hottest.get("courseName"));
        summary.put("hottestApplied", hottest == null ? 0 : hottest.get("applied"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("round", r);
        out.put("summary", summary);
        out.put("courses", rows.stream().limit(12).toList());
        return out;
    }

    /** 候补：漏斗、队列排行、等待时长与加入节奏 */
    public Map<String, Object> waitlistAnalysis(Integer round) {
        int r = (round == null || round < 1) ? currentRound() : round;
        List<Waitlist> list = waitlistRepo.findByRound(r);
        Map<Long, Course> cm = new HashMap<>();
        for (Course c : courseRepo.findAll()) cm.put(c.getId(), c);

        int waiting = 0, confirming = 0, confirmed = 0, timeout = 0, cancelled = 0, promoted = 0;
        List<Long> waits = new ArrayList<>();
        for (Waitlist w : list) {
            String s = w.getStatus() == null ? "" : w.getStatus();
            switch (s) {
                case "waiting" -> waiting++;
                case "confirming" -> confirming++;
                case "confirmed" -> confirmed++;
                case "timeout" -> timeout++;
                case "cancelled" -> cancelled++;
                default -> { }
            }
            boolean isPromoted = w.getPromoteTime() != null
                    || "confirming".equals(s) || "confirmed".equals(s) || "timeout".equals(s);
            if (isPromoted) promoted++;
            if (w.getPromoteTime() != null && w.getJoinTime() != null) {
                long mins = Duration.between(w.getJoinTime(), w.getPromoteTime()).toMinutes();
                if (mins >= 0) waits.add(mins);
            }
        }
        Collections.sort(waits);
        double[] waitArr = waits.stream().mapToDouble(Long::doubleValue).toArray();
        double avgWait = waits.isEmpty() ? 0 : waits.stream().mapToLong(Long::longValue).average().orElse(0);

        int joined = list.size();

        // 按课程聚合候补队列
        Map<Long, List<Waitlist>> byCourse = new LinkedHashMap<>();
        for (Waitlist w : list) byCourse.computeIfAbsent(w.getCourseId(), k -> new ArrayList<>()).add(w);
        Map<Long, Integer> approvedByCourse = new HashMap<>();
        for (Enrollment e : enrollmentRepo.findByRound(r)) {
            if ("approved".equals(e.getStatus()))
                approvedByCourse.merge(e.getCourseId(), 1, Integer::sum);
        }
        List<Map<String, Object>> queues = new ArrayList<>();
        for (Map.Entry<Long, List<Waitlist>> en : byCourse.entrySet()) {
            Course c = cm.get(en.getKey());
            List<Waitlist> ws = en.getValue();
            int qConfirmed = 0, qWaiting = 0, qTimeout = 0, qCancelled = 0;
            List<Long> qWaits = new ArrayList<>();
            for (Waitlist w : ws) {
                String s = w.getStatus() == null ? "" : w.getStatus();
                if ("confirmed".equals(s)) qConfirmed++;
                else if ("timeout".equals(s)) qTimeout++;
                else if ("cancelled".equals(s)) qCancelled++;
                else qWaiting++;
                if (w.getPromoteTime() != null && w.getJoinTime() != null) {
                    long mins = Duration.between(w.getJoinTime(), w.getPromoteTime()).toMinutes();
                    if (mins >= 0) qWaits.add(mins);
                }
            }
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("courseId", en.getKey());
            q.put("courseName", c == null ? "已删除课程" : c.getName());
            q.put("category", c == null ? "" : c.getCategory());
            q.put("capacity", c == null ? 0 : nz(c.getCapacity()));
            q.put("approved", approvedByCourse.getOrDefault(en.getKey(), 0));
            q.put("joined", ws.size());
            q.put("waiting", qWaiting);
            q.put("confirmed", qConfirmed);
            q.put("timeout", qTimeout);
            q.put("cancelled", qCancelled);
            q.put("avgWaitMinutes", qWaits.isEmpty() ? 0 : round1(qWaits.stream().mapToLong(Long::longValue).average().orElse(0)));
            queues.add(q);
        }
        queues.sort((a, b) -> Integer.compare((int) b.get("joined"), (int) a.get("joined")));

        // 等待时长分布（从加入候补到收到递补）
        String[] bucketLabels = {"<30 分钟", "30 分钟-2 小时", "2-6 小时", "6-12 小时", "12-24 小时", ">24 小时"};
        long[] bucketEdges = {0, 30, 120, 360, 720, 1440, Long.MAX_VALUE};
        int[] buckets = new int[6];
        for (long m : waits) {
            for (int i = 0; i < 6; i++) {
                if (m >= bucketEdges[i] && m < bucketEdges[i + 1]) { buckets[i]++; break; }
            }
        }
        List<Map<String, Object>> waitBuckets = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("label", bucketLabels[i]);
            b.put("count", buckets[i]);
            waitBuckets.add(b);
        }

        // 最近 7 天加入节奏（以数据中最后一天为终点，避免历史数据全为 0）
        LocalDate end = list.stream().map(Waitlist::getJoinTime).filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate).max(LocalDate::compareTo).orElse(LocalDate.now());
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = end.minusDays(i);
            int cnt = (int) list.stream().filter(w -> w.getJoinTime() != null
                    && w.getJoinTime().toLocalDate().equals(d)).count();
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("date", d.format(MONTH_DAY));
            t.put("count", cnt);
            trend.add(t);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", r);
        summary.put("joined", joined);
        summary.put("promoted", promoted);
        summary.put("confirmed", confirmed);
        summary.put("waiting", waiting);
        summary.put("confirming", confirming);
        summary.put("timeout", timeout);
        summary.put("cancelled", cancelled);
        summary.put("active", waiting + confirming);
        summary.put("avgWaitMinutes", round1(avgWait));
        summary.put("medianWaitMinutes", waits.isEmpty() ? 0.0 : round1(quantile(waitArr, .5)));
        summary.put("promotionRate", pct(promoted, joined));
        summary.put("confirmRate", pct(confirmed, joined));
        summary.put("confirmOfPromoted", pct(confirmed, promoted));
        summary.put("timeoutRate", pct(timeout, joined));
        summary.put("cancelRate", pct(cancelled, joined));

        List<Map<String, Object>> funnel = new ArrayList<>();
        funnel.add(funnelStage("joined", "加入候补", joined, 100.0));
        funnel.add(funnelStage("promoted", "收到递补", promoted, pct(promoted, joined)));
        funnel.add(funnelStage("confirmed", "确认成功", confirmed, pct(confirmed, joined)));

        List<Map<String, Object>> statuses = new ArrayList<>();
        statuses.add(statusItem("waiting", "排队中", waiting));
        statuses.add(statusItem("confirming", "待确认", confirming));
        statuses.add(statusItem("confirmed", "已确认", confirmed));
        statuses.add(statusItem("timeout", "已超时", timeout));
        statuses.add(statusItem("cancelled", "已取消", cancelled));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("round", r);
        out.put("summary", summary);
        out.put("funnel", funnel);
        out.put("statuses", statuses);
        out.put("queues", queues.stream().limit(8).toList());
        out.put("waitBuckets", waitBuckets);
        out.put("trend", trend);
        return out;
    }

    private Map<String, Object> funnelStage(String key, String label, int count, double rate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("count", count);
        m.put("rate", rate);
        return m;
    }

    private Map<String, Object> statusItem(String key, String label, int count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("count", count);
        return m;
    }

    /** 教室/排课：星期 × 时段热力、热门地点、楼宇与校区分布 */
    public Map<String, Object> classroomAnalysis() {
        int round = currentRound();
        List<Course> courses = courseRepo.findAll();

        // 当前轮次每门课的录取人数（同一学生在同一课程只计一次）
        Map<Long, Set<Long>> approvedStudents = new HashMap<>();
        for (Enrollment e : enrollmentRepo.findByRound(round)) {
            if ("approved".equals(e.getStatus()))
                approvedStudents.computeIfAbsent(e.getCourseId(), k -> new HashSet<>()).add(e.getStudentId());
        }

        int[][] courseCount = new int[5][7];
        int[][] studentCount = new int[5][7];
        int[][] capCount = new int[5][7];
        Map<String, LinkedHashSet<String>> cellNames = new HashMap<>();
        Set<Long> scheduledCourses = new HashSet<>();
        int slotPairs = 0;

        Map<String, Set<Long>> locCourses = new LinkedHashMap<>();
        Map<String, Integer> locStudents = new LinkedHashMap<>();
        Map<String, Integer> locCapacity = new LinkedHashMap<>();
        Map<String, Set<Long>> buildingCourses = new LinkedHashMap<>();
        Map<String, Integer> buildingStudents = new LinkedHashMap<>();
        Map<String, Integer> campusCount = new LinkedHashMap<>();

        for (Course c : courses) {
            List<String[]> slots = parseSlots(c.getTime());
            if (slots.isEmpty()) continue;
            scheduledCourses.add(c.getId());
            int students = approvedStudents.getOrDefault(c.getId(), Collections.emptySet()).size();

            for (String[] s : slots) {
                int d = dayIndex(s[0]);
                int p = periodIndex(s[1]);
                if (d < 0 || p < 0) continue;
                courseCount[p][d]++;
                studentCount[p][d] += students;
                capCount[p][d] += nz(c.getCapacity());
                cellNames.computeIfAbsent(p + "-" + d, k -> new LinkedHashSet<>()).add(c.getName());
                slotPairs++;
            }

            String loc = c.getLocation() == null || c.getLocation().isBlank() ? "未标注" : c.getLocation().trim();
            locCourses.computeIfAbsent(loc, k -> new LinkedHashSet<>()).add(c.getId());
            locStudents.merge(loc, students, Integer::sum);
            locCapacity.merge(loc, nz(c.getCapacity()), Integer::sum);
            String building = buildingOf(loc);
            buildingCourses.computeIfAbsent(building, k -> new LinkedHashSet<>()).add(c.getId());
            buildingStudents.merge(building, students, Integer::sum);
            campusCount.merge(c.getCampus() == null || c.getCampus().isBlank() ? "未标注" : c.getCampus(), 1, Integer::sum);
        }

        List<List<Map<String, Object>>> cells = new ArrayList<>();
        int peakP = -1, peakD = -1, peakCourses = 0, peakStudents = 0, nonEmpty = 0;
        for (int p = 0; p < 5; p++) {
            List<Map<String, Object>> row = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("day", DAY_LABELS[d]);
                cell.put("period", PERIOD_LABELS[p]);
                cell.put("range", PERIOD_RANGES[p]);
                cell.put("courses", courseCount[p][d]);
                cell.put("students", studentCount[p][d]);
                cell.put("capacity", capCount[p][d]);
                LinkedHashSet<String> names = cellNames.getOrDefault(p + "-" + d, new LinkedHashSet<>());
                cell.put("names", names.stream().limit(5).toList());
                cell.put("more", Math.max(0, names.size() - 5));
                row.add(cell);

                if (courseCount[p][d] > 0) nonEmpty++;
                if (courseCount[p][d] > peakCourses
                        || (courseCount[p][d] == peakCourses && studentCount[p][d] > peakStudents)) {
                    peakCourses = courseCount[p][d];
                    peakStudents = studentCount[p][d];
                    peakP = p;
                    peakD = d;
                }
            }
            cells.add(row);
        }

        List<Map<String, Object>> locations = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> en : locCourses.entrySet()) {
            String loc = en.getKey();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", loc);
            m.put("building", buildingOf(loc));
            m.put("courses", en.getValue().size());
            m.put("students", locStudents.getOrDefault(loc, 0));
            m.put("capacity", locCapacity.getOrDefault(loc, 0));
            locations.add(m);
        }
        locations.sort((a, b) -> {
            int c = Integer.compare((int) b.get("courses"), (int) a.get("courses"));
            return c != 0 ? c : Integer.compare((int) b.get("students"), (int) a.get("students"));
        });

        List<Map<String, Object>> buildings = new ArrayList<>();
        for (Map.Entry<String, Set<Long>> en : buildingCourses.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", en.getKey());
            m.put("courses", en.getValue().size());
            m.put("students", buildingStudents.getOrDefault(en.getKey(), 0));
            buildings.add(m);
        }
        buildings.sort((a, b) -> Integer.compare((int) b.get("courses"), (int) a.get("courses")));

        List<Map<String, Object>> campuses = new ArrayList<>();
        for (Map.Entry<String, Integer> en : campusCount.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", en.getKey());
            m.put("courses", en.getValue());
            campuses.add(m);
        }
        campuses.sort((a, b) -> Integer.compare((int) b.get("courses"), (int) a.get("courses")));

        List<Map<String, Object>> periodLoad = new ArrayList<>();
        for (int p = 0; p < 5; p++) {
            int cc = 0, ss = 0;
            for (int d = 0; d < 7; d++) { cc += courseCount[p][d]; ss += studentCount[p][d]; }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", PERIOD_LABELS[p]);
            m.put("range", PERIOD_RANGES[p]);
            m.put("courses", cc);
            m.put("students", ss);
            periodLoad.add(m);
        }

        List<Map<String, Object>> dayLoad = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            int cc = 0, ss = 0;
            for (int p = 0; p < 5; p++) { cc += courseCount[p][d]; ss += studentCount[p][d]; }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", DAY_LABELS[d]);
            m.put("courses", cc);
            m.put("students", ss);
            dayLoad.add(m);
        }

        String topBuilding = buildings.isEmpty() ? "—" : (String) buildings.get(0).get("name");
        int topBuildingCount = buildings.isEmpty() ? 0 : (int) buildings.get(0).get("courses");
        int approvedTotal = scheduledCourses.stream()
                .mapToInt(id -> approvedStudents.getOrDefault(id, Collections.emptySet()).size()).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", round);
        summary.put("totalCourses", courses.size());
        summary.put("scheduledCourses", scheduledCourses.size());
        summary.put("slotPairs", slotPairs);
        summary.put("coverage", pct(nonEmpty, 35));
        summary.put("freeSlots", 35 - nonEmpty);
        summary.put("peakDay", peakP < 0 ? "—" : DAY_LABELS[peakD]);
        summary.put("peakPeriod", peakP < 0 ? "—" : PERIOD_RANGES[peakP]);
        summary.put("peakCourses", peakCourses);
        summary.put("peakStudents", peakStudents);
        summary.put("topBuilding", topBuilding);
        summary.put("topBuildingCount", topBuildingCount);
        summary.put("avgClassSize", scheduledCourses.isEmpty() ? 0.0 : round1((double) approvedTotal / scheduledCourses.size()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("round", round);
        out.put("summary", summary);
        out.put("days", Arrays.asList(DAY_LABELS));
        out.put("periods", Arrays.asList(PERIOD_LABELS));
        out.put("periodRanges", Arrays.asList(PERIOD_RANGES));
        out.put("cells", cells);
        out.put("locations", locations.stream().limit(10).toList());
        out.put("buildings", buildings.stream().limit(8).toList());
        out.put("campuses", campuses);
        out.put("periodLoad", periodLoad);
        out.put("dayLoad", dayLoad);
        return out;
    }

    /** 成绩分析：总分分布、课程均分对比、平时/期末相关性与年级差异 */
    public Map<String, Object> gradeAnalysis() {
        List<Grade> all = gradeRepo.findAll();
        Map<Long, Course> cm = new HashMap<>();
        for (Course c : courseRepo.findAll()) cm.put(c.getId(), c);

        // 总体指标 + 10 分一段的总分分布
        int records = all.size();
        int scored = 0, passCount = 0, excellentCount = 0;
        double totalSum = 0;
        Double maxTotal = null, minTotal = null;
        int[] buckets = new int[10];
        for (Grade g : all) {
            Double t = g.getTotalScore();
            if (t == null) continue;
            scored++;
            totalSum += t;
            if (t >= 60) passCount++;
            if (t >= 90) excellentCount++;
            maxTotal = maxTotal == null ? t : Math.max(maxTotal, t);
            minTotal = minTotal == null ? t : Math.min(minTotal, t);
            buckets[Math.max(0, Math.min(9, (int) Math.floor(t / 10)))]++;
        }
        List<Map<String, Object>> dist = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("low", i * 10);
            b.put("high", i * 10 + 9);
            b.put("count", buckets[i]);
            dist.add(b);
        }

        // 平时 / 期末散点（均匀采样至多 400 点）与皮尔逊相关系数
        List<Grade> pairs = all.stream()
                .filter(g -> g.getRegularScore() != null && g.getFinalScore() != null).toList();
        double regSum = 0, finSum = 0;
        for (Grade g : pairs) { regSum += g.getRegularScore(); finSum += g.getFinalScore(); }
        double corr = 0;
        if (pairs.size() >= 2) {
            double mR = regSum / pairs.size(), mF = finSum / pairs.size();
            double cov = 0, vr = 0, vf = 0;
            for (Grade g : pairs) {
                double dr = g.getRegularScore() - mR, df = g.getFinalScore() - mF;
                cov += dr * df; vr += dr * dr; vf += df * df;
            }
            corr = vr == 0 || vf == 0 ? 0 : cov / Math.sqrt(vr * vf);
        }
        int step = Math.max(1, (int) Math.ceil(pairs.size() / 400.0));
        List<Map<String, Object>> scatter = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i += step) {
            Grade g = pairs.get(i);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("r", round1(g.getRegularScore()));
            p.put("f", round1(g.getFinalScore()));
            p.put("t", g.getTotalScore() == null ? null : round1(g.getTotalScore()));
            p.put("c", g.getCourseId());
            scatter.add(p);
        }

        // 课程维度：均分 / 及格率 / 优秀率 / 平时与期末均值
        Map<Long, List<Grade>> byCourse = new LinkedHashMap<>();
        for (Grade g : all) byCourse.computeIfAbsent(g.getCourseId(), k -> new ArrayList<>()).add(g);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<Grade>> en : byCourse.entrySet()) {
            Course c = cm.get(en.getKey());
            List<Double> totals = new ArrayList<>();
            double reg = 0, fin = 0;
            int regN = 0, finN = 0;
            for (Grade g : en.getValue()) {
                if (g.getTotalScore() != null) totals.add(g.getTotalScore());
                if (g.getRegularScore() != null) { reg += g.getRegularScore(); regN++; }
                if (g.getFinalScore() != null) { fin += g.getFinalScore(); finN++; }
            }
            if (totals.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", en.getKey());
            m.put("courseName", c == null ? "已删除课程" : c.getName());
            m.put("category", c == null ? "" : c.getCategory());
            m.put("teacherName", c == null ? "" : courseService.teacherName(c.getTeacherId()));
            m.put("count", totals.size());
            m.put("avg", round1(totals.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
            m.put("max", round1(totals.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
            m.put("min", round1(totals.stream().mapToDouble(Double::doubleValue).min().orElse(0)));
            m.put("avgRegular", regN == 0 ? 0.0 : round1(reg / regN));
            m.put("avgFinal", finN == 0 ? 0.0 : round1(fin / finN));
            m.put("passRate", pct((int) totals.stream().filter(v -> v >= 60).count(), totals.size()));
            m.put("excellentRate", pct((int) totals.stream().filter(v -> v >= 90).count(), totals.size()));
            rows.add(m);
        }
        rows.sort((a, b) -> {
            int c = Integer.compare((int) b.get("count"), (int) a.get("count"));
            return c != 0 ? c : Long.compare((long) a.get("courseId"), (long) b.get("courseId"));
        });

        // 年级维度：同一年级学生的总分均值
        Map<Long, User> um = new HashMap<>();
        for (User u : userRepo.findAll()) um.put(u.getId(), u);
        Map<Integer, List<Double>> byCohort = new TreeMap<>();
        for (Grade g : all) {
            if (g.getTotalScore() == null) continue;
            User u = um.get(g.getStudentId());
            if (u == null || u.getGrade() == null) continue;
            byCohort.computeIfAbsent(u.getGrade(), k -> new ArrayList<>()).add(g.getTotalScore());
        }
        List<Map<String, Object>> cohorts = new ArrayList<>();
        for (Map.Entry<Integer, List<Double>> en : byCohort.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("grade", en.getKey());
            m.put("count", en.getValue().size());
            m.put("avg", round1(en.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0)));
            cohorts.add(m);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("records", records);
        summary.put("scored", scored);
        summary.put("courses", rows.size());
        summary.put("avg", scored == 0 ? 0.0 : round1(totalSum / scored));
        summary.put("max", maxTotal == null ? 0.0 : round1(maxTotal));
        summary.put("min", minTotal == null ? 0.0 : round1(minTotal));
        summary.put("passRate", pct(passCount, scored));
        summary.put("excellentRate", pct(excellentCount, scored));
        summary.put("failCount", scored - passCount);
        summary.put("excellentCount", excellentCount);
        summary.put("avgRegular", pairs.isEmpty() ? 0.0 : round1(regSum / pairs.size()));
        summary.put("avgFinal", pairs.isEmpty() ? 0.0 : round1(finSum / pairs.size()));
        summary.put("correlation", pairs.size() < 2 ? null : round1(corr));
        summary.put("scatterCount", pairs.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("courses", rows.stream().limit(12).toList());
        out.put("dist", dist);
        out.put("scatter", scatter);
        out.put("cohorts", cohorts);
        return out;
    }

    /** 意向分析：在册意向 → 报名/录取流转、投分偏差、课程意向排行 */
    public Map<String, Object> intentAnalysis(Integer round) {
        int r = (round == null || round < 1) ? currentRound() : round;
        List<CourseIntent> intents = intentRepo.findAll();
        Map<Long, Course> cm = new HashMap<>();
        for (Course c : courseRepo.findAll()) cm.put(c.getId(), c);

        // 本轮报名：同一学生对同一课程保留未退课的那条（否则保留已退课条）
        Map<String, Enrollment> enrolled = new HashMap<>();
        for (Enrollment e : enrollmentRepo.findByRound(r)) {
            String k = e.getStudentId() + ":" + e.getCourseId();
            Enrollment old = enrolled.get(k);
            if (old == null || ("dropped".equals(old.getStatus()) && !"dropped".equals(e.getStatus()))) {
                enrolled.put(k, e);
            }
        }
        Set<String> waitlisted = new HashSet<>();
        for (Waitlist w : waitlistRepo.findByRound(r)) {
            waitlisted.add(w.getStudentId() + ":" + w.getCourseId());
        }

        int total = intents.size();
        int cntEnrolled = 0, cntApproved = 0, cntWaitlist = 0, cntRejected = 0,
                cntDropped = 0, cntPending = 0, cntUnrolled = 0;
        Set<Long> students = new HashSet<>(), courses = new HashSet<>();
        int pairs = 0, exact = 0, desiredCount = 0;
        double absDeltaSum = 0, desiredSum = 0;

        // 偏差分桶：实际投分 − 预填意向分
        String[] deltaLabels = {"大幅调低 ≤-10", "小幅调低 -9~-1", "保持一致 0", "小幅调高 1~9", "大幅调高 ≥10"};
        int[] deltas = new int[5];

        Map<Long, int[]> byCourse = new LinkedHashMap<>();   // [意向数, 已报名, 录取]
        for (CourseIntent it : intents) {
            students.add(it.getStudentId());
            courses.add(it.getCourseId());
            int[] cc = byCourse.computeIfAbsent(it.getCourseId(), k -> new int[3]);
            cc[0]++;
            if (it.getDesiredPoints() != null) { desiredSum += it.getDesiredPoints(); desiredCount++; }

            Enrollment e = enrolled.get(it.getStudentId() + ":" + it.getCourseId());
            if (e == null) {
                cntUnrolled++;
                continue;
            }
            cntEnrolled++;
            cc[1]++;
            String st = e.getStatus() == null ? "" : e.getStatus();
            switch (st) {
                case "approved" -> { cntApproved++; cc[2]++; }
                case "dropped" -> cntDropped++;
                case "rejected" -> {
                    if (waitlisted.contains(it.getStudentId() + ":" + it.getCourseId())) cntWaitlist++;
                    else cntRejected++;
                }
                default -> cntPending++;
            }
            if (it.getDesiredPoints() != null && e.getPoints() != null) {
                int d = e.getPoints() - it.getDesiredPoints();
                pairs++;
                absDeltaSum += Math.abs(d);
                if (d == 0) { exact++; deltas[2]++; }
                else if (d <= -10) deltas[0]++;
                else if (d < 0) deltas[1]++;
                else if (d < 10) deltas[3]++;
                else deltas[4]++;
            }
        }

        List<Map<String, Object>> flow = new ArrayList<>();
        flow.add(funnelStage("total", "在册意向", total, 100.0));
        flow.add(funnelStage("enrolled", "已报名该课", cntEnrolled, pct(cntEnrolled, total)));
        flow.add(funnelStage("approved", "成功录取", cntApproved, pct(cntApproved, total)));
        flow.add(funnelStage("waitlist", "进入候补", cntWaitlist, pct(cntWaitlist, total)));
        flow.add(funnelStage("rejected", "落选", cntRejected, pct(cntRejected, total)));
        flow.add(funnelStage("pending", "待结算", cntPending, pct(cntPending, total)));
        flow.add(funnelStage("dropped", "报名后又退课", cntDropped, pct(cntDropped, total)));
        flow.add(funnelStage("unrolled", "尚未报名", cntUnrolled, pct(cntUnrolled, total)));

        List<Map<String, Object>> deltaBuckets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("label", deltaLabels[i]);
            b.put("count", deltas[i]);
            deltaBuckets.add(b);
        }

        List<Map<String, Object>> courseRows = new ArrayList<>();
        for (Map.Entry<Long, int[]> en : byCourse.entrySet()) {
            Course c = cm.get(en.getKey());
            int[] cc = en.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", en.getKey());
            m.put("courseName", c == null ? "已删除课程" : c.getName());
            m.put("category", c == null ? "" : c.getCategory());
            m.put("teacherName", c == null ? "" : courseService.teacherName(c.getTeacherId()));
            m.put("capacity", c == null ? 0 : nz(c.getCapacity()));
            m.put("intents", cc[0]);
            m.put("enrolled", cc[1]);
            m.put("approved", cc[2]);
            m.put("convertRate", pct(cc[1], cc[0]));
            courseRows.add(m);
        }
        courseRows.sort((a, b) -> {
            int c = Integer.compare((int) b.get("intents"), (int) a.get("intents"));
            return c != 0 ? c : Long.compare((long) a.get("courseId"), (long) b.get("courseId"));
        });

        // 最近 7 天意向新增节奏
        LocalDate end = intents.stream().map(CourseIntent::getCreatedAt).filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate).max(LocalDate::compareTo).orElse(LocalDate.now());
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = end.minusDays(i);
            int cnt = (int) intents.stream().filter(x -> x.getCreatedAt() != null
                    && x.getCreatedAt().toLocalDate().equals(d)).count();
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("date", d.format(MONTH_DAY));
            t.put("count", cnt);
            trend.add(t);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", r);
        summary.put("intents", total);
        summary.put("students", students.size());
        summary.put("courses", courses.size());
        summary.put("enrolled", cntEnrolled);
        summary.put("approved", cntApproved);
        summary.put("waitlist", cntWaitlist);
        summary.put("rejected", cntRejected);
        summary.put("pending", cntPending);
        summary.put("dropped", cntDropped);
        summary.put("unrolled", cntUnrolled);
        summary.put("convertRate", pct(cntEnrolled, total));
        summary.put("approveRate", pct(cntApproved, total));
        summary.put("avgDesired", desiredCount == 0 ? 0.0 : round1(desiredSum / desiredCount));
        summary.put("exactCount", exact);
        summary.put("exactRate", pct(exact, pairs));
        summary.put("avgAbsDelta", pairs == 0 ? 0.0 : round1(absDeltaSum / pairs));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("round", r);
        out.put("summary", summary);
        out.put("flow", flow);
        out.put("deltaBuckets", deltaBuckets);
        out.put("courses", courseRows.stream().limit(8).toList());
        out.put("trend", trend);
        return out;
    }

    /* ============================================================
       实时监控
       ============================================================ */

    private boolean isEnrollOp(String op) {
        return op != null && (op.contains("/enrollments/enroll") || op.contains("/waitlist/join"));
    }

    private String actionLabel(String op) {
        if (op == null) return "选课相关操作";
        if (op.contains("/enrollments/enroll")) return "报名课程";
        if (op.contains("/enrollments/drop")) return "退课 / 取消报名";
        if (op.contains("/enrollments/settle")) return "轮次结算";
        if (op.contains("/waitlist/join")) return "加入候补";
        if (op.contains("/waitlist")) return "候补操作";
        if (op.contains("/intents")) return "意向操作";
        return "选课相关操作";
    }

    /** 实时监控：逐分钟操作节奏、瞬时峰值、课程报名赛跑与最新动态 */
    public Map<String, Object> liveAnalysis() {
        int round = currentRound();
        LocalDateTime now = LocalDateTime.now();
        SystemConfig cfg = sysRepo.findById(1L).orElse(null);
        List<OperationLog> recent = logRepo.findByCreatedAtBetweenOrderByCreatedAtAsc(now.minusMinutes(60), now.plusSeconds(1));

        // 最近 30 分钟逐分钟桶：[总操作, 报名类, 失败数]
        Map<Integer, int[]> buckets = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) buckets.put(i, new int[3]);
        int enrollLastHour = 0, todayEnrolls = 0;
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        for (OperationLog l : recent) {
            if (l.getCreatedAt() == null) continue;
            boolean enroll = isEnrollOp(l.getOperation());
            if (enroll) {
                enrollLastHour++;
                if (!l.getCreatedAt().isBefore(dayStart)) todayEnrolls++;
            }
            long ago = Duration.between(l.getCreatedAt(), now).toMinutes();
            if (ago >= 0 && ago <= 29) {
                int[] b = buckets.get((int) ago);
                b[0]++;
                if (enroll) b[1]++;
                if (!"成功".equals(l.getStatus())) b[2]++;
            }
        }
        List<Map<String, Object>> timeline = new ArrayList<>();
        int peakOps = 0;
        String peakMinute = "—";
        for (Map.Entry<Integer, int[]> en : buckets.entrySet()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("minute", now.minusMinutes(en.getKey()).format(HOUR_MIN));
            t.put("ops", en.getValue()[0]);
            t.put("enrolls", en.getValue()[1]);
            t.put("fails", en.getValue()[2]);
            timeline.add(t);
            if (en.getValue()[0] > peakOps) {
                peakOps = en.getValue()[0];
                peakMinute = (String) t.get("minute");
            }
        }

        List<Enrollment> cur = enrollmentRepo.findByRound(round);
        long pendingNow = cur.stream().filter(e -> "pending".equals(e.getStatus())).count();
        long approvedNow = cur.stream().filter(e -> "approved".equals(e.getStatus())).count();

        // 课程报名赛跑（非必修）：报名 / 录取 / 候补
        Map<Long, int[]> byCourse = new LinkedHashMap<>();   // [applied, approved]
        for (Enrollment e : cur) {
            if ("dropped".equals(e.getStatus())) continue;
            int[] c = byCourse.computeIfAbsent(e.getCourseId(), k -> new int[2]);
            c[0]++;
            if ("approved".equals(e.getStatus())) c[1]++;
        }
        List<Map<String, Object>> race = new ArrayList<>();
        for (Course c : courseRepo.findAll()) {
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            int[] cc = byCourse.get(c.getId());
            int applied = cc == null ? 0 : cc[0];
            int waiting = waitlistRepo.findByCourseIdAndRoundAndStatusInOrderByJoinTimeAsc(c.getId(), round, WaitlistService.ACTIVE).size();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("applied", applied);
            m.put("approved", cc == null ? 0 : cc[1]);
            m.put("capacity", nz(c.getCapacity()));
            m.put("fillRate", pct(applied, nz(c.getCapacity())));
            m.put("waiting", waiting);
            race.add(m);
        }
        race.sort((a, b) -> {
            int d = Double.compare((double) b.get("fillRate"), (double) a.get("fillRate"));
            return d != 0 ? d : Integer.compare((int) b.get("applied"), (int) a.get("applied"));
        });

        // 最新动态：优先展示真实操作（POST），读操作仅补位；安静时回退到历史最近 12 条
        List<OperationLog> feedRaw = new ArrayList<>(recent);
        feedRaw.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        List<Map<String, Object>> actions = new ArrayList<>(), reads = new ArrayList<>();
        for (OperationLog l : feedRaw) {
            if (!"选课管理".equals(l.getType())) continue;
            boolean isRead = l.getOperation() != null && l.getOperation().startsWith("GET");
            (isRead ? reads : actions).add(feedItem(l));
            if (actions.size() >= 12 && reads.size() >= 12) break;
        }
        List<Map<String, Object>> feed = new ArrayList<>(actions);
        for (Map<String, Object> r : reads) {
            if (feed.size() >= 12) break;
            feed.add(r);
        }
        if (feed.isEmpty()) {
            for (OperationLog l : logRepo.findAllByOrderByCreatedAtDesc()) {
                if (!"选课管理".equals(l.getType())) continue;
                feed.add(feedItem(l));
                if (feed.size() >= 12) break;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", round);
        summary.put("roundOpen", cfg == null || cfg.getRoundOpen() == null || cfg.getRoundOpen());
        summary.put("opsLastHour", recent.size());
        summary.put("enrollLastHour", enrollLastHour);
        summary.put("peakOps", peakOps);
        summary.put("peakMinute", peakMinute);
        summary.put("pendingNow", pendingNow);
        summary.put("approvedNow", approvedNow);
        summary.put("todayEnrolls", todayEnrolls);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("round", round);
        out.put("openAt", cfg == null ? null : cfg.getRoundOpenAt());
        out.put("closeAt", cfg == null ? null : cfg.getRoundCloseAt());
        out.put("serverTime", now.format(TIME_SEC));
        out.put("summary", summary);
        out.put("timeline", timeline);
        out.put("race", race.stream().limit(12).toList());
        out.put("feed", feed);
        return out;
    }

    private Map<String, Object> feedItem(OperationLog l) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("time", l.getCreatedAt() == null ? "" : l.getCreatedAt().format(TIME_SEC));
        f.put("userName", l.getUserName());
        f.put("action", actionLabel(l.getOperation()));
        f.put("status", l.getStatus());
        return f;
    }

    /** 公平性分析：录取率、同分抽签占比、学生录取分布基尼系数、年级间均衡度 */
    public Map<String, Object> fairnessAnalysis(Integer round) {
        int r = (round == null || round < 1) ? currentRound() : round;
        List<Course> courses = courseRepo.findAll();
        Map<Long, Course> cm = new HashMap<>();
        for (Course c : courses) cm.put(c.getId(), c);

        // 学生维度：本轮（选修）报名/录取；课程维度聚合
        Map<Long, int[]> byStudent = new HashMap<>();        // [applied, approved]
        Map<Long, List<Enrollment>> byCourse = new LinkedHashMap<>();
        for (Enrollment e : enrollmentRepo.findByRound(r)) {
            if ("dropped".equals(e.getStatus())) continue;
            Course c = cm.get(e.getCourseId());
            if (c == null || (c.getCategory() != null && c.getCategory().contains("必修"))) continue;
            int[] s = byStudent.computeIfAbsent(e.getStudentId(), k -> new int[2]);
            s[0]++;
            byCourse.computeIfAbsent(e.getCourseId(), k -> new ArrayList<>()).add(e);
            if ("approved".equals(e.getStatus())) s[1]++;
        }

        int applied = 0, approved = 0, tieRejected = 0, highRejected = 0, rejectedTotal = 0, lotteryCourses = 0;
        double cutSum = 0; int cutCount = 0;
        List<int[]> perStudent = new ArrayList<>(byStudent.values());
        for (int[] s : perStudent) { applied += s[0]; approved += s[1]; }
        double approvalRate = pct(approved, applied);
        double competition = approved == 0 ? 0.0 : round1((double) applied / approved);

        // 课程维度：录取线、同分抽签、冲突落选
        List<Map<String, Object>> courseRows = new ArrayList<>();
        for (Map.Entry<Long, List<Enrollment>> en : byCourse.entrySet()) {
            Course c = cm.get(en.getKey());
            List<Enrollment> es = en.getValue();
            List<Enrollment> ap = es.stream().filter(e -> "approved".equals(e.getStatus())).toList();
            List<Enrollment> rj = es.stream().filter(e -> "rejected".equals(e.getStatus())).toList();
            int rej = rj.size();
            rejectedTotal += rej;
            Integer cutline = null;
            if (!ap.isEmpty() && !rj.isEmpty()) {
                cutline = ap.stream().mapToInt(e -> nz(e.getPoints())).min().orElse(0);
                cutSum += cutline; cutCount++;
            }
            int tRej = 0, hRej = 0;
            if (cutline != null) {
                for (Enrollment e : rj) {
                    int p = nz(e.getPoints());
                    if (p > cutline) hRej++;
                    else if (p == cutline) tRej++;
                }
            }
            tieRejected += tRej; highRejected += hRej;
            if (tRej > 0) lotteryCourses++;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", en.getKey());
            m.put("courseName", c == null ? "已删除课程" : c.getName());
            m.put("teacherName", c == null ? "" : courseService.teacherName(c.getTeacherId()));
            m.put("capacity", c == null ? 0 : nz(c.getCapacity()));
            m.put("applied", es.size());
            m.put("approved", ap.size());
            m.put("rejected", rej);
            m.put("approvalRate", pct(ap.size(), es.size()));
            m.put("cutline", cutline);
            m.put("tieRejected", tRej);
            m.put("highRejected", hRej);
            courseRows.add(m);
        }
        courseRows.sort((a, b) -> Integer.compare((int) b.get("rejected"), (int) a.get("rejected")));

        // 学生录取数分布（0/1/2/3/4/5+ 门）与基尼系数
        int[] ad = new int[6];
        for (int[] s : perStudent) ad[Math.min(5, s[1])]++;
        List<Map<String, Object>> approveDist = new ArrayList<>();
        String[] adLabels = {"0 门", "1 门", "2 门", "3 门", "4 门", "5 门及以上"};
        for (int i = 0; i < 6; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", adLabels[i]);
            m.put("count", ad[i]);
            approveDist.add(m);
        }
        double gini = gini(perStudent.stream().mapToDouble(s -> s[1]).toArray());

        // 个人录取率分布（报名 ≥2 门的学生）
        int[] rb = new int[5];
        String[] rbLabels = {"0% 全落空", "1-39%", "40-69%", "70-99%", "100% 全录取"};
        for (int[] s : perStudent) {
            if (s[0] < 2) continue;
            double rate = s[0] == 0 ? 0 : (double) s[1] / s[0];
            int b = rate <= 0 ? 0 : rate < 0.4 ? 1 : rate < 0.7 ? 2 : rate < 1 ? 3 : 4;
            rb[b]++;
        }
        List<Map<String, Object>> rateBuckets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", rbLabels[i]);
            m.put("count", rb[i]);
            rateBuckets.add(m);
        }

        // 年级间均衡度：各年级报名学生的录取率极差
        Map<Long, User> um = new HashMap<>();
        for (User u : userRepo.findAll()) um.put(u.getId(), u);
        Map<Integer, int[]> byGrade = new TreeMap<>();
        for (Map.Entry<Long, int[]> en : byStudent.entrySet()) {
            User u = um.get(en.getKey());
            if (u == null || u.getGrade() == null) continue;
            int[] g = byGrade.computeIfAbsent(u.getGrade(), k -> new int[3]);
            g[0]++;                       // 人数
            g[1] += en.getValue()[0];     // 报名
            g[2] += en.getValue()[1];     // 录取
        }
        List<Map<String, Object>> gradeRows = new ArrayList<>();
        double gradeMax = 0, gradeMin = 100;
        for (Map.Entry<Integer, int[]> en : byGrade.entrySet()) {
            int[] g = en.getValue();
            double rate = pct(g[2], g[1]);
            gradeMax = Math.max(gradeMax, rate);
            gradeMin = Math.min(gradeMin, rate);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("grade", en.getKey());
            m.put("students", g[0]);
            m.put("applied", g[1]);
            m.put("approved", g[2]);
            m.put("approvalRate", rate);
            gradeRows.add(m);
        }

        // 轮次公平性趋势：各轮平均录取线、录取率、抽签落选数
        List<Map<String, Object>> roundTrend = new ArrayList<>();
        int maxRound = enrollmentRepo.findAll().stream()
                .filter(e -> e.getRound() != null).mapToInt(Enrollment::getRound).max().orElse(r);
        maxRound = Math.max(maxRound, r);
        for (int rr = 1; rr <= maxRound; rr++) {
            final int frr = rr;
            Map<Long, int[]> st = new HashMap<>();
            double cs = 0; int cc = 0, a = 0, p = 0, tie = 0;
            for (Enrollment e : enrollmentRepo.findByRound(rr)) {
                if ("dropped".equals(e.getStatus())) continue;
                Course c = cm.get(e.getCourseId());
                if (c == null || (c.getCategory() != null && c.getCategory().contains("必修"))) continue;
                int[] s = st.computeIfAbsent(e.getStudentId(), k -> new int[2]);
                s[0]++; p++;
                if ("approved".equals(e.getStatus())) { s[1]++; a++; }
            }
            for (Course c : courses) {
                if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
                List<Enrollment> es = enrollmentRepo.findByCourseId(c.getId()).stream()
                        .filter(e -> e.getRound() != null && e.getRound() == frr && !"dropped".equals(e.getStatus()))
                        .toList();
                List<Enrollment> ap = es.stream().filter(e -> "approved".equals(e.getStatus())).toList();
                List<Enrollment> rj = es.stream().filter(e -> "rejected".equals(e.getStatus())).toList();
                if (!ap.isEmpty() && !rj.isEmpty()) {
                    int cut = ap.stream().mapToInt(e -> nz(e.getPoints())).min().orElse(0);
                    cs += cut; cc++;
                    tie += rj.stream().filter(e -> nz(e.getPoints()) == cut).count();
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("round", rr);
            m.put("applied", p);
            m.put("approved", a);
            m.put("approvalRate", pct(a, p));
            m.put("avgCutline", cc == 0 ? 0.0 : round1(cs / cc));
            m.put("tieRejected", tie);
            roundTrend.add(m);
        }

        double tieShare = rejectedTotal == 0 ? 0.0 : tieRejected * 100.0 / rejectedTotal;
        double highShare = rejectedTotal == 0 ? 0.0 : highRejected * 100.0 / rejectedTotal;
        double gradeGap = gradeRows.size() < 2 ? 0.0 : round1(gradeMax - gradeMin);
        double competitionPenalty = Math.max(0, competition - 2) * 4;
        double score = 100 - gini * 45 - tieShare * 0.25 - highShare * 0.10 - competitionPenalty;
        int fairnessScore = (int) Math.max(0, Math.min(100, Math.round(score)));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", r);
        summary.put("students", perStudent.size());
        summary.put("applied", applied);
        summary.put("approved", approved);
        summary.put("rejected", rejectedTotal);
        summary.put("approvalRate", approvalRate);
        summary.put("competition", competition);
        summary.put("gini", Math.round(gini * 1000.0) / 1000.0);
        summary.put("fairnessScore", fairnessScore);
        summary.put("tieRejected", tieRejected);
        summary.put("highRejected", highRejected);
        summary.put("tieShare", round1(tieShare));
        summary.put("highShare", round1(highShare));
        summary.put("lotteryCourses", lotteryCourses);
        summary.put("avgCutline", cutCount == 0 ? 0.0 : round1(cutSum / cutCount));
        summary.put("gradeGap", gradeGap);

        // 抽签热点（同分落选最多）
        List<Map<String, Object>> lottery = new ArrayList<>(courseRows);
        lottery.sort((a, b) -> Integer.compare((int) b.get("tieRejected"), (int) a.get("tieRejected")));
        lottery.removeIf(x -> (int) x.get("tieRejected") <= 0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("round", r);
        out.put("summary", summary);
        out.put("approveDist", approveDist);
        out.put("rateBuckets", rateBuckets);
        out.put("roundTrend", roundTrend);
        out.put("courses", courseRows.stream().limit(12).toList());
        out.put("lottery", lottery.stream().limit(8).toList());
        out.put("grades", gradeRows);
        return out;
    }

    /** 基尼系数（0=完全均衡，1=完全不均） */
    private double gini(double[] x) {
        int n = x.length;
        double sum = 0;
        for (double v : x) sum += v;
        if (n == 0 || sum == 0) return 0;
        Arrays.sort(x);
        double cum = 0;
        for (int i = 0; i < n; i++) cum += (i + 1) * x[i];
        return (2 * cum - (n + 1) * sum) / (n * sum);
    }

    /* ============================================================
       性能风控（QPS + 异常告警）
       ============================================================ */

    private boolean isFailLog(OperationLog l) {
        return "失败".equals(l.getStatus());
    }

    /** 性能风控：QPS 节奏、失败率、异常高频用户、拥挤课程与告警列表 */
    public Map<String, Object> riskAnalysis() {
        int round = currentRound();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        List<OperationLog> recent = logRepo.findByCreatedAtBetweenOrderByCreatedAtAsc(now.minusMinutes(60), now.plusSeconds(1));

        // 30 分钟逐分钟桶：[总操作, 报名类, 失败]
        Map<Integer, int[]> buckets = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) buckets.put(i, new int[3]);
        Map<String, Integer> failByEndpoint = new LinkedHashMap<>();
        Map<String, Integer> opsByUser = new LinkedHashMap<>();
        int opsLastHour = 0, enrollLastHour = 0, failLastHour = 0, todayOps = 0, todayFails = 0;
        Set<String> activeUsers = new HashSet<>();
        for (OperationLog l : recent) {
            if (l.getCreatedAt() == null) continue;
            boolean enroll = isEnrollOp(l.getOperation());
            boolean fail = isFailLog(l);
            opsLastHour++;
            if (enroll) enrollLastHour++;
            if (fail) failLastHour++;
            if (!l.getCreatedAt().isBefore(dayStart)) { todayOps++; if (fail) todayFails++; }
            if (l.getUserName() != null) {
                activeUsers.add(l.getUserName());
                opsByUser.merge(l.getUserName(), 1, Integer::sum);
            }
            long ago = Duration.between(l.getCreatedAt(), now).toMinutes();
            if (ago >= 0 && ago <= 29) {
                int[] b = buckets.get((int) ago);
                b[0]++;
                if (enroll) b[1]++;
                if (fail) b[2]++;
            }
            if (fail) {
                String op = l.getOperation() == null ? "未知接口" : l.getOperation();
                failByEndpoint.merge(op, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> timeline = new ArrayList<>();
        int peakOps = 0; String peakMinute = "—";
        for (Map.Entry<Integer, int[]> en : buckets.entrySet()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("minute", now.minusMinutes(en.getKey()).format(HOUR_MIN));
            t.put("ops", en.getValue()[0]);
            t.put("enrolls", en.getValue()[1]);
            t.put("fails", en.getValue()[2]);
            timeline.add(t);
            if (en.getValue()[0] > peakOps) { peakOps = en.getValue()[0]; peakMinute = (String) t.get("minute"); }
        }
        double qpsNow = Math.round(opsLastHour / 3600.0 * 100.0) / 100.0;
        double qpsPeak = Math.round(peakOps / 60.0 * 100.0) / 100.0;
        double failRate = pct(failLastHour, opsLastHour);

        // 高频用户（疑似脚本）：近 1 小时操作次数排行
        List<Map<String, Object>> topUsers = new ArrayList<>();
        opsByUser.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(6)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userName", e.getKey());
                    m.put("ops", e.getValue());
                    m.put("suspicious", e.getValue() > 120);
                    topUsers.add(m);
                });

        // 拥挤课程：满员 + 候补堆积
        List<Enrollment> cur = enrollmentRepo.findByRound(round);
        Map<Long, int[]> byCourse = new LinkedHashMap<>();   // [applied, approved]
        for (Enrollment e : cur) {
            if ("dropped".equals(e.getStatus())) continue;
            int[] c = byCourse.computeIfAbsent(e.getCourseId(), k -> new int[2]);
            c[0]++;
            if ("approved".equals(e.getStatus())) c[1]++;
        }
        long pendingNow = cur.stream().filter(e -> "pending".equals(e.getStatus())).count();
        List<Map<String, Object>> hotCourses = new ArrayList<>();
        int maxWaitingCourse = 0;
        for (Course c : courseRepo.findAll()) {
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            int[] cc = byCourse.get(c.getId());
            int appliedC = cc == null ? 0 : cc[0];
            if (appliedC == 0) continue;
            int waiting = waitlistRepo.findByCourseIdAndRoundAndStatusInOrderByJoinTimeAsc(c.getId(), round, WaitlistService.ACTIVE).size();
            maxWaitingCourse = Math.max(maxWaitingCourse, waiting);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", c.getId());
            m.put("courseName", c.getName());
            m.put("applied", appliedC);
            m.put("capacity", nz(c.getCapacity()));
            m.put("fillRate", pct(cc == null ? 0 : cc[1], nz(c.getCapacity())));
            m.put("waiting", waiting);
            m.put("crowded", nz(c.getCapacity()) > 0 && cc != null && cc[1] >= nz(c.getCapacity()) && waiting > 0);
            hotCourses.add(m);
        }
        hotCourses.sort((a, b) -> {
            int d = Double.compare((double) b.get("fillRate"), (double) a.get("fillRate"));
            return d != 0 ? d : Integer.compare((int) b.get("waiting"), (int) a.get("waiting"));
        });

        // 告警规则
        List<Map<String, Object>> alerts = new ArrayList<>();
        String nowText = now.format(TIME_SEC);
        long scriptUsers = topUsers.stream().filter(u -> (boolean) u.get("suspicious")).count();
        if (failRate > 20) {
            alerts.add(alert("danger", "失败率过高", "近 1 小时接口失败率 " + failRate + "%，请检查服务端状态", nowText));
        } else if (failRate > 5) {
            alerts.add(alert("warn", "失败率偏高", "近 1 小时接口失败率 " + failRate + "%，建议关注失败接口", nowText));
        }
        if (peakOps > 150) {
            alerts.add(alert("danger", "瞬时高并发", peakMinute + " 操作量达 " + peakOps + " 次/分钟（QPS " + qpsPeak + "），已超过承载阈值", nowText));
        } else if (peakOps > 60) {
            alerts.add(alert("warn", "流量上涨", peakMinute + " 操作量 " + peakOps + " 次/分钟，注意观察服务负载", nowText));
        }
        if (scriptUsers > 0) {
            alerts.add(alert("warn", "疑似脚本刷课", scriptUsers + " 个账号近 1 小时操作超过 120 次，建议人工核验", nowText));
        }
        long crowded = hotCourses.stream().filter(x -> (boolean) x.get("crowded")).count();
        if (crowded > 0) {
            alerts.add(alert("info", "课程拥挤", crowded + " 门课程满员且存在候补，最大候补队列 " + maxWaitingCourse + " 人", nowText));
        }
        if (alerts.isEmpty()) {
            alerts.add(alert("info", "系统运行平稳", "近 1 小时无异常流量、无失败率告警、无高频异常账号", nowText));
        }

        double health = 100 - failRate * 1.5
                - (peakOps > 150 ? 25 : peakOps > 60 ? 10 : 0)
                - scriptUsers * 8
                - Math.min(10, maxWaitingCourse / 5.0);
        int healthScore = (int) Math.max(0, Math.min(100, Math.round(health)));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", round);
        summary.put("qpsNow", qpsNow);
        summary.put("qpsPeak", qpsPeak);
        summary.put("peakMinute", peakMinute);
        summary.put("peakOps", peakOps);
        summary.put("opsLastHour", opsLastHour);
        summary.put("enrollLastHour", enrollLastHour);
        summary.put("todayOps", todayOps);
        summary.put("failLastHour", failLastHour);
        summary.put("failRate", failRate);
        summary.put("todayFails", todayFails);
        summary.put("activeUsers", activeUsers.size());
        summary.put("pendingNow", pendingNow);
        summary.put("crowded", crowded);
        summary.put("scriptUsers", scriptUsers);
        summary.put("healthScore", healthScore);

        List<Map<String, Object>> failList = new ArrayList<>();
        failByEndpoint.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("operation", e.getKey());
                    m.put("count", e.getValue());
                    failList.add(m);
                });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serverTime", now.format(TIME_SEC));
        out.put("summary", summary);
        out.put("timeline", timeline);
        out.put("alerts", alerts);
        out.put("failByEndpoint", failList);
        out.put("topUsers", topUsers);
        out.put("hotCourses", hotCourses.stream().limit(6).toList());
        return out;
    }

    private Map<String, Object> alert(String level, String title, String detail, String time) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("title", title);
        m.put("detail", detail);
        m.put("time", time);
        return m;
    }

    /* ============================================================
       运营分析（供需 / 余量热力 / 退课 / 毕业预警）
       ============================================================ */

    /** 运营分析：供需缺口 + 名额余量热力 + 退课统计 + 毕业预警 */
    public Map<String, Object> operationAnalysis() {
        int round = currentRound();
        List<Course> courses = courseRepo.findAll();
        Map<Long, Course> cm = new HashMap<>();
        for (Course c : courses) cm.put(c.getId(), c);

        // 当前轮报名/录取（选修）
        Map<Long, int[]> byCourse = new LinkedHashMap<>();   // [applied, approved, dropped]
        for (Enrollment e : enrollmentRepo.findByRound(round)) {
            Course c = cm.get(e.getCourseId());
            if (c == null || (c.getCategory() != null && c.getCategory().contains("必修"))) continue;
            int[] a = byCourse.computeIfAbsent(e.getCourseId(), k -> new int[3]);
            if ("dropped".equals(e.getStatus())) { a[2]++; continue; }
            a[0]++;
            if ("approved".equals(e.getStatus())) a[1]++;
        }

        /* ---- #31 供需缺口：需求高但名额少的课程，给出加开建议 ---- */
        List<Map<String, Object>> supplyGap = new ArrayList<>();
        for (Map.Entry<Long, int[]> en : byCourse.entrySet()) {
            Course c = cm.get(en.getKey());
            int[] a = en.getValue();
            int cap = nz(c.getCapacity());
            if (a[0] == 0) continue;
            int deficit = Math.max(0, a[0] - cap);
            if (deficit <= 0 && a[2] <= 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", en.getKey());
            m.put("courseName", c.getName());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("time", c.getTime());
            m.put("applied", a[0]);
            m.put("capacity", cap);
            m.put("deficit", deficit);
            m.put("dropped", a[2]);
            m.put("ratio", cap <= 0 ? 0 : round1((double) a[0] / cap));
            m.put("suggest", deficit > 0
                    ? "建议加开 " + Math.max(1, (int) Math.ceil(deficit * 1.0 / Math.max(1, cap))) + " 个班（缺口 " + deficit + " 人）"
                    : "容量充足，关注退课原因");
            supplyGap.add(m);
        }
        supplyGap.sort((a, b) -> {
            int d = Integer.compare((int) b.get("deficit"), (int) a.get("deficit"));
            return d != 0 ? d : Double.compare((double) b.get("ratio"), (double) a.get("ratio"));
        });
        supplyGap = new ArrayList<>(supplyGap.stream().limit(10).toList());

        /* ---- #27 名额余量热力：星期 × 时段，值 = 剩余名额占比（越低越紧张）---- */
        int[][] capGrid = new int[5][7];
        int[][] remainGrid = new int[5][7];
        int[][] courseGrid = new int[5][7];
        Map<String, LinkedHashSet<String>> gridNames = new HashMap<>();
        for (Course c : courses) {
            if (c.getCategory() != null && c.getCategory().contains("必修")) continue;
            List<String[]> slots = parseSlots(c.getTime());
            if (slots.isEmpty()) continue;
            int[] a = byCourse.getOrDefault(c.getId(), new int[3]);
            int remain = Math.max(0, nz(c.getCapacity()) - a[1]);
            for (String[] s : slots) {
                int d = dayIndex(s[0]);
                int p = periodIndex(s[1]);
                if (d < 0 || p < 0) continue;
                capGrid[p][d] += nz(c.getCapacity());
                remainGrid[p][d] += remain;
                courseGrid[p][d]++;
                gridNames.computeIfAbsent(p + "-" + d, k -> new LinkedHashSet<>()).add(c.getName());
            }
        }
        List<List<Map<String, Object>>> heat = new ArrayList<>();
        int worstP = -1, worstD = -1; double worstTension = -1;
        for (int p = 0; p < 5; p++) {
            List<Map<String, Object>> row = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                int cap = capGrid[p][d];
                int remain = remainGrid[p][d];
                double tension = cap <= 0 ? 0 : round1((1 - remain * 1.0 / cap) * 100);   // 0 空 ~ 100 满
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("day", DAY_LABELS[d]);
                cell.put("period", PERIOD_LABELS[p]);
                cell.put("range", PERIOD_RANGES[p]);
                cell.put("capacity", cap);
                cell.put("remaining", remain);
                cell.put("courses", courseGrid[p][d]);
                cell.put("tension", tension);
                LinkedHashSet<String> names = gridNames.getOrDefault(p + "-" + d, new LinkedHashSet<>());
                cell.put("names", names.stream().limit(4).toList());
                cell.put("more", Math.max(0, names.size() - 4));
                row.add(cell);
                if (tension > worstTension) { worstTension = tension; worstP = p; worstD = d; }
            }
            heat.add(row);
        }

        /* ---- #34 退课统计：退课率排行（全轮累计）+ 各轮退课节奏 ---- */
        Map<Long, int[]> dropByCourse = new LinkedHashMap<>();  // [totalRecords, dropped]
        Map<Integer, Integer> dropByRound = new TreeMap<>();
        int totalRecords = 0, totalDropped = 0;
        for (Enrollment e : enrollmentRepo.findAll()) {
            Course c = cm.get(e.getCourseId());
            if (c == null || (c.getCategory() != null && c.getCategory().contains("必修"))) continue;
            int r = e.getRound() == null ? 0 : e.getRound();
            boolean dropped = "dropped".equals(e.getStatus());
            totalRecords++;
            if (dropped) { totalDropped++; dropByRound.merge(r, 1, Integer::sum); }
            int[] a = dropByCourse.computeIfAbsent(e.getCourseId(), k -> new int[2]);
            a[0]++;
            if (dropped) a[1]++;
        }
        List<Map<String, Object>> dropoutRank = new ArrayList<>();
        for (Map.Entry<Long, int[]> en : dropByCourse.entrySet()) {
            Course c = cm.get(en.getKey());
            int[] a = en.getValue();
            if (a[0] < 3 || a[1] == 0) continue;   // 报名少于 3 人的不参与排行
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", en.getKey());
            m.put("courseName", c.getName());
            m.put("teacherName", courseService.teacherName(c.getTeacherId()));
            m.put("records", a[0]);
            m.put("dropped", a[1]);
            m.put("rate", pct(a[1], a[0]));
            dropoutRank.add(m);
        }
        dropoutRank.sort((a, b) -> Double.compare((double) b.get("rate"), (double) a.get("rate")));
        dropoutRank = new ArrayList<>(dropoutRank.stream().limit(8).toList());
        List<Map<String, Object>> roundDrops = new ArrayList<>();
        for (Map.Entry<Integer, Integer> en : dropByRound.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("round", en.getKey());
            m.put("dropped", en.getValue());
            roundDrops.add(m);
        }

        /* ---- #35 毕业预警：按专业统计毕业风险学生 ---- */
        SystemConfig cfg = sysRepo.findById(1L).orElse(null);
        Map<String, Integer> reqMap = new LinkedHashMap<>();
        reqMap.put("必修", nz(cfg == null ? 0 : cfg.getGradReqRequired()));
        int pubElec = cfg != null && cfg.getGradReqGeneral() != null && cfg.getGradReqGeneral() > 0
                ? cfg.getGradReqGeneral() : 8;
        reqMap.put("公共选修", pubElec);   // 华厦公选 8 学分硬指标
        reqMap.put("专业选修", nz(cfg == null ? 0 : cfg.getGradReqElective()));
        String[] KEYS = com.zhixue.util.CourseGroup.KEYS;   // 必修 / 公共选修 / 专业选修

        Map<Long, Map<Long, Double>> passedScore = new HashMap<>();    // studentId -> courseId -> score
        // 毕业预警与毕业/GPA 同口径：按成绩流水有效记录（最新一次通过优先）
        for (Map.Entry<Long, Map<Long, GradeRecord>> se : ledger.effectiveRecordsAll().entrySet()) {
            for (Map.Entry<Long, GradeRecord> ce : se.getValue().entrySet()) {
                Double sc = ce.getValue().getTotalScore();
                if (sc != null && sc >= GradeService.PASS_LINE) {
                    passedScore.computeIfAbsent(se.getKey(), k -> new HashMap<>()).put(ce.getKey(), sc);
                }
            }
        }
        Map<Long, Set<Long>> approvedIds = new HashMap<>();
        for (Enrollment e : enrollmentRepo.findAll()) {
            if (!"approved".equals(e.getStatus())) continue;
            approvedIds.computeIfAbsent(e.getStudentId(), k -> new HashSet<>()).add(e.getCourseId());
        }
        List<Course> requiredCourses = courses.stream()
                .filter(c -> c.getCategory() != null && c.getCategory().contains("必修")).toList();

        List<Map<String, Object>> riskStudents = new ArrayList<>();
        Map<String, Object[]> byMajor = new LinkedHashMap<>();  // major -> [students, high, gapSum]
        int high = 0, mid = 0, low = 0, safe = 0;
        for (User u : userRepo.findAll()) {
            if (!"student".equals(u.getRole())) continue;
            Set<Long> approved = approvedIds.getOrDefault(u.getId(), Collections.emptySet());
            Map<Long, Double> ps = passedScore.getOrDefault(u.getId(), Collections.emptyMap());
            Map<String, Integer> earned = new LinkedHashMap<>();
            Map<String, Integer> inProgress = new LinkedHashMap<>();
            for (String k : KEYS) { earned.put(k, 0); inProgress.put(k, 0); }
            List<String> missingRequired = new ArrayList<>();
            for (Long cid : approved) {
                Course c = cm.get(cid);
                if (c == null) continue;
                if (!com.zhixue.util.CourseGroup.countsForGraduation(c.getCourseCode())) continue;
                String g = com.zhixue.util.CourseGroup.groupOf(c.getCategory());
                if (ps.containsKey(cid)) {
                    // 已通过：计入已获学分
                    if (earned.containsKey(g)) earned.merge(g, nz(c.getCredit()), Integer::sum);
                } else {
                    // 已录取未出成绩（或未通过）：算「在修中」，预计可获学分可抵扣缺口
                    if (inProgress.containsKey(g)) inProgress.merge(g, nz(c.getCredit()), Integer::sum);
                }
            }
            for (Course c : requiredCourses) {
                // 必修课既没通过也没在修 → 真缺失
                if (!ps.containsKey(c.getId()) && !approved.contains(c.getId())) missingRequired.add(c.getName());
            }
            int hardGap = 0, projGap = 0;
            List<Map<String, Object>> gaps = new ArrayList<>();
            for (String k : KEYS) {
                int hard = Math.max(0, reqMap.get(k) - earned.get(k));
                int proj = Math.max(0, reqMap.get(k) - earned.get(k) - inProgress.get(k));
                hardGap += hard;
                projGap += proj;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", k);
                m.put("required", reqMap.get(k));
                m.put("earned", earned.get(k));
                m.put("inProgress", inProgress.get(k));
                m.put("gap", proj);
                gaps.add(m);
            }
            // 未通过/未在修的必修课视为无法抵扣的硬缺口，直接计入预计缺口
            projGap += missingRequired.size() * 2;
            String level = projGap <= 0 ? "safe" : projGap >= 10 ? "high" : projGap >= 5 ? "mid" : "low";
            if ("high".equals(level)) high++; else if ("mid".equals(level)) mid++; else if ("low".equals(level)) low++; else safe++;

            String major = (u.getMajor() == null || u.getMajor().isBlank()) ? "未标注" : u.getMajor();
            Object[] agg = byMajor.computeIfAbsent(major, k -> new Object[]{0, 0, 0});
            agg[0] = (int) agg[0] + 1;
            if ("high".equals(level)) agg[1] = (int) agg[1] + 1;
            agg[2] = (int) agg[2] + projGap;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", u.getId());
            m.put("name", u.getName());
            m.put("no", u.getNo());
            m.put("major", major);
            m.put("grade", u.getGrade());
            m.put("gap", projGap);
            m.put("hardGap", hardGap);
            m.put("level", level);
            m.put("gaps", gaps);
            m.put("missingRequired", missingRequired.stream().limit(4).toList());
            riskStudents.add(m);
        }
        riskStudents.sort(Comparator.comparingInt(s -> -((int) s.get("gap"))));
        List<Map<String, Object>> majorRows = new ArrayList<>();
        for (Map.Entry<String, Object[]> en : byMajor.entrySet()) {
            Object[] a = en.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("major", en.getKey());
            m.put("students", (int) a[0]);
            m.put("high", (int) a[1]);
            m.put("avgGap", round1((int) a[2] * 1.0 / Math.max(1, (int) a[0])));
            majorRows.add(m);
        }
        majorRows.sort((a, b) -> Integer.compare((int) b.get("high"), (int) a.get("high")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("round", round);
        summary.put("supplyShortage", (int) supplyGap.stream().filter(s -> (int) s.get("deficit") > 0).count());
        summary.put("worstCell", worstP >= 0 ? DAY_LABELS[worstD] + " " + PERIOD_LABELS[worstP] : "—");
        summary.put("worstTension", worstTension < 0 ? 0 : worstTension);
        summary.put("totalDropped", totalDropped);
        summary.put("dropoutRate", pct(totalDropped, totalRecords));
        summary.put("riskStudents", riskStudents.size());
        summary.put("riskHigh", high);
        summary.put("riskMid", mid);
        summary.put("riskLow", low);
        summary.put("riskSafe", safe);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("supplyGap", supplyGap);
        out.put("heat", heat);
        out.put("dropoutRank", dropoutRank);
        out.put("roundDrops", roundDrops);
        out.put("byMajor", majorRows);
        out.put("riskStudents", riskStudents.stream().limit(20).toList());
        return out;
    }

    /* ============================================================
       课程评价分析
       ============================================================ */

    /** 评价分析常用词种子（词云在分词之外补充这些整词） */
    private static final String[] SEED_WORDS = {
            "老师", "讲课", "认真", "负责", "有趣", "生动", "干货", "实用", "轻松", "严格",
            "温柔", "耐心", "推荐", "避雷", "收获", "清晰", "易懂", "互动", "氛围", "考试",
            "作业", "给分", "点名", "签到", "小组", "论文", "展示", "实践", "案例", "入门",
            "硬核", "喜欢", "优秀", "专业", "压力", "干货多", "水分"
    };
    private static final String STOP_CHARS =
            "的了是我很也都就不有人在啊呢吧吗这那个们和与及或但而被把让向往从对对于来说上下中里外之为以等还又再才只更最挺蛮非常十分别然因此所以因为如果虽然不过居然竟然其实确实总觉会能可以要去想过真给有点些时地过得下上好多";

    private static boolean isHan(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

    private static int countOf(String s, String w) {
        int n = 0, i = 0;
        while ((i = s.indexOf(w, i)) >= 0) { n++; i += w.length(); }
        return n;
    }

    /** 中文词云（无分词器）：二字词组挖掘 + 种子词补充，停用字过滤 */
    private List<Map<String, Object>> wordCloud(List<CourseReview> reviews, int limit) {
        List<String> contents = new ArrayList<>();
        for (CourseReview r : reviews) {
            if (r.getContent() != null && !r.getContent().isBlank()) {
                contents.add(r.getContent().replaceAll("\\s+", ""));
            }
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String s : contents) {
            for (int i = 0; i + 1 < s.length(); i++) {
                char a = s.charAt(i), b = s.charAt(i + 1);
                if (!isHan(a) || !isHan(b)) continue;
                if (STOP_CHARS.indexOf(a) >= 0 || STOP_CHARS.indexOf(b) >= 0) continue;
                counts.merge(s.substring(i, i + 2), 1, Integer::sum);
            }
        }
        for (String w : SEED_WORDS) {
            int c = 0;
            for (String s : contents) c += countOf(s, w);
            if (c > 0) {
                Integer old = counts.get(w);
                counts.put(w, old == null ? c : Math.max(old, c));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("word", e.getKey());
                    m.put("count", e.getValue());
                    out.add(m);
                });
        return out;
    }

    /** 评价分析：星级分布、评分 vs 热度散点、维度均分与评论词云 */
    public Map<String, Object> reviewAnalysis() {
        int round = currentRound();
        List<CourseReview> reviews = reviewRepo.findAll();
        reviews.sort((a, b) -> {
            if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        int total = 0;
        double sum = 0, tSum = 0, cSum = 0, hSum = 0;
        int tN = 0, cN = 0, hN = 0;
        int[] dist = new int[6];
        Set<Long> students = new HashSet<>();
        Map<Long, List<Integer>> byCourse = new LinkedHashMap<>();
        for (CourseReview r : reviews) {
            if (r.getRating() == null) continue;
            total++;
            int rt = Math.max(1, Math.min(5, r.getRating()));
            dist[rt]++;
            sum += rt;
            students.add(r.getStudentId());
            byCourse.computeIfAbsent(r.getCourseId(), k -> new ArrayList<>()).add(rt);
            if (r.getTeachingRating() != null) { tSum += r.getTeachingRating(); tN++; }
            if (r.getContentRating() != null) { cSum += r.getContentRating(); cN++; }
            if (r.getHomeworkRating() != null) { hSum += r.getHomeworkRating(); hN++; }
        }

        List<Map<String, Object>> distribution = new ArrayList<>();
        for (int s = 5; s >= 1; s--) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("star", s);
            m.put("count", dist[s]);
            m.put("rate", pct(dist[s], total));
            distribution.add(m);
        }

        List<Map<String, Object>> dims = new ArrayList<>();
        dims.add(dimension("teaching", "教学水平", tSum, tN));
        dims.add(dimension("content", "教学内容", cSum, cN));
        dims.add(dimension("homework", "作业安排", hSum, hN));

        // 评分 vs 报名热度散点（当前轮次）
        Map<Long, Course> cm = new HashMap<>();
        for (Course c : courseRepo.findAll()) cm.put(c.getId(), c);
        Map<Long, Integer> appliedMap = new HashMap<>();
        for (Enrollment e : enrollmentRepo.findByRound(round)) {
            if ("dropped".equals(e.getStatus())) continue;
            appliedMap.merge(e.getCourseId(), 1, Integer::sum);
        }
        List<Map<String, Object>> scatter = new ArrayList<>();
        List<double[]> corrPairs = new ArrayList<>();
        for (Map.Entry<Long, List<Integer>> en : byCourse.entrySet()) {
            Course c = cm.get(en.getKey());
            List<Integer> ratings = en.getValue();
            double avg = ratings.stream().mapToInt(Integer::intValue).average().orElse(0);
            int applied = appliedMap.getOrDefault(en.getKey(), 0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", en.getKey());
            m.put("courseName", c == null ? "已删除课程" : c.getName());
            m.put("avg", round1(avg));
            m.put("count", ratings.size());
            m.put("applied", applied);
            m.put("capacity", c == null ? 0 : nz(c.getCapacity()));
            scatter.add(m);
            corrPairs.add(new double[]{avg, applied});
        }
        scatter.sort((a, b) -> Integer.compare((int) b.get("applied"), (int) a.get("applied")));
        Double correlation = null;
        if (corrPairs.size() >= 3) {
            double mR = corrPairs.stream().mapToDouble(p -> p[0]).average().orElse(0);
            double mA = corrPairs.stream().mapToDouble(p -> p[1]).average().orElse(0);
            double cov = 0, vr = 0, va = 0;
            for (double[] p : corrPairs) {
                cov += (p[0] - mR) * (p[1] - mA);
                vr += (p[0] - mR) * (p[0] - mR);
                va += (p[1] - mA) * (p[1] - mA);
            }
            if (vr > 0 && va > 0) correlation = round1(cov / Math.sqrt(vr * va));
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (CourseReview r : reviews) {
            if (total > 0 && recent.size() >= 8) break;
            Course c = cm.get(r.getCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseName", c == null ? "已删除课程" : c.getName());
            m.put("rating", r.getRating());
            m.put("content", r.getContent());
            m.put("createdAt", r.getCreatedAt());
            m.put("anonymous", r.getAnonymous());
            recent.add(m);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("avgRating", total == 0 ? 0.0 : round1(sum / total));
        summary.put("ratedCourses", byCourse.size());
        summary.put("students", students.size());
        summary.put("goodRate", pct(dist[4] + dist[5], total));
        summary.put("avgTeaching", tN == 0 ? 0.0 : round1(tSum / tN));
        summary.put("avgContent", cN == 0 ? 0.0 : round1(cSum / cN));
        summary.put("avgHomework", hN == 0 ? 0.0 : round1(hSum / hN));
        summary.put("correlation", correlation);
        summary.put("round", round);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("distribution", distribution);
        out.put("dims", dims);
        out.put("scatter", scatter.stream().limit(30).toList());
        out.put("cloud", wordCloud(reviews, 36));
        out.put("recent", recent);
        return out;
    }

    private Map<String, Object> dimension(String key, String label, double sum, int n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("avg", n == 0 ? 0.0 : round1(sum / n));
        return m;
    }

    private List<String[]> parseSlots(String timeStr) {
        List<String[]> out = new ArrayList<>();
        if (timeStr == null || timeStr.isBlank()) return out;
        for (String part : timeStr.split("/")) {
            Matcher m = COURSE_TIME.matcher(part.trim());
            if (m.find()) out.add(new String[]{m.group(1), m.group(2)});
        }
        return out;
    }

    private int dayIndex(String day) {
        if (day == null || day.length() < 2) return -1;
        return "一二三四五六日".indexOf(day.charAt(1));
    }

    private int periodIndex(String hhmm) {
        if (hhmm == null || hhmm.length() < 5) return -1;
        int h, m;
        try {
            h = Integer.parseInt(hhmm.substring(0, 2));
            m = Integer.parseInt(hhmm.substring(3, 5));
        } catch (NumberFormatException e) {
            return -1;
        }
        double t = h + m / 60.0;
        if (t < 9.5) return 0;
        if (t < 12) return 1;
        if (t < 15.5) return 2;
        if (t < 18) return 3;
        return 4;
    }

    private String buildingOf(String loc) {
        if (loc == null || loc.isBlank()) return "未标注";
        int i = loc.lastIndexOf('-');
        return i > 0 ? loc.substring(0, i) : loc;
    }
}
