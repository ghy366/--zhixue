package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.GradeRequirement;
import com.zhixue.entity.RoundSchedule;
import com.zhixue.entity.StaggerWindow;
import com.zhixue.entity.SystemConfig;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.CapacityService;
import com.zhixue.service.RoundScheduleService;
import com.zhixue.service.StaggerService;
import com.zhixue.service.SystemService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired private SystemService systemService;
    @Autowired private RoundScheduleService roundScheduleService;
    @Autowired private StaggerService staggerService;
    @Autowired private CapacityService capacityService;
    @Autowired private UserRepository userRepository;

    /** 轮次时间设置请求体 */
    public static class ScheduleReq {
        public Boolean autoSchedule;
        public List<RoundSchedule> schedules;
    }

    @GetMapping("/config")
    public ApiResponse<SystemConfig> config() {
        return ApiResponse.ok(systemService.getConfig());
    }

    /**
     * 全校当前学期（教务端统一口径）。
     * 教师/管理员没有 grade，无法按个人推算；以「在读学生人数最多的年级」为基准，
     * 套用与学生端相同的学期公式并叠加学期偏移，保证教务端看到的学期 = 大多数学生所处学期。
     */
    @GetMapping("/current-semester")
    public ApiResponse<Map<String, Object>> currentSemester() {
        SystemConfig cfg = systemService.getConfig();
        int offset = cfg.getSemesterOffset() == null ? 0 : cfg.getSemesterOffset();
        int grade = 0;
        long best = -1;
        for (Object[] row : userRepository.countStudentsByGrade()) {
            if (row[0] == null) continue;
            int g = ((Number) row[0]).intValue();
            long c = ((Number) row[1]).longValue();
            if (c > best) { best = c; grade = g; }
        }
        int sem = com.zhixue.service.GraduationService.currentSemesterNo(
                grade > 0 ? grade : null, java.time.LocalDate.now(), offset);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("semesterNo", sem);
        r.put("grade", grade);
        r.put("semesterOffset", offset);
        r.put("students", Math.max(0, best));
        return ApiResponse.ok(r);
    }

    /** 读取三轮时间窗口 + 当前自动推导出的状态 */
    @GetMapping("/round-schedule")
    public ApiResponse<Map<String, Object>> getRoundSchedule() {
        Map<String, Object> r = new LinkedHashMap<>(roundScheduleService.status());
        r.put("schedules", roundScheduleService.list());
        return ApiResponse.ok(r);
    }

    /** 保存三轮时间窗口 / 自动开关，并立即同步状态 */
    @PostMapping("/round-schedule")
    public ApiResponse<Map<String, Object>> saveRoundSchedule(@RequestBody ScheduleReq body) {
        SystemConfig c = roundScheduleService.save(body.schedules, body.autoSchedule);
        String msg;
        if (Boolean.TRUE.equals(c.getAutoSchedule())) {
            msg = "时间设置已保存，系统将按设定时间自动开放与结束选课";
            if (Boolean.TRUE.equals(c.getRoundOpen())) msg += "（当前第 " + c.getCurrentRound() + " 轮进行中）";
            else if (c.getRoundOpenAt() != null) msg += "（第 " + c.getCurrentRound() + " 轮尚未开放）";
            else msg += "（当前无开放轮次）";
        } else {
            msg = "时间设置已保存，自动模式已关闭，当前为手动控制";
        }
        Map<String, Object> r = new LinkedHashMap<>(roundScheduleService.status());
        r.put("schedules", roundScheduleService.list());
        return ApiResponse.ok(msg, r);
    }

    /** 错峰开放配置：读取全部「轮次+年级→开放时间」 */
    @GetMapping("/stagger")
    public ApiResponse<List<StaggerWindow>> getStagger() {
        return ApiResponse.ok(staggerService.listAll());
    }

    /** 错峰开放配置：全量覆盖保存（仅第 1、2 轮有效，第三轮不参与错峰） */
    @PostMapping("/stagger")
    public ApiResponse<List<StaggerWindow>> saveStagger(@RequestBody List<StaggerWindow> body) {
        List<StaggerWindow> saved = staggerService.saveAll(body);
        return ApiResponse.ok("错峰开放配置已保存（共 " + saved.size() + " 条，仅第 1、2 轮生效）", saved);
    }

    /** 学生年级分布（入学年份 → 人数），错峰配置界面用它生成候选年级 */
    @GetMapping("/grade-stats")
    public ApiResponse<Map<String, Object>> gradeStats() {
        List<User> students = userRepository.findByRole("student");
        Map<Integer, Long> counts = students.stream()
                .filter(u -> u.getGrade() != null)
                .collect(Collectors.groupingBy(User::getGrade, TreeMap::new, Collectors.counting()));
        List<Map<String, Object>> items = counts.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("grade", e.getKey());
            m.put("count", e.getValue());
            return m;
        }).collect(Collectors.toList());
        long unknown = students.stream().filter(u -> u.getGrade() == null).count();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", items);
        r.put("unknownCount", unknown);
        return ApiResponse.ok(r);
    }

    /** 学生查询自己在当前轮的错峰开放时间 */
    @GetMapping("/my-window")
    public ApiResponse<Map<String, Object>> myWindow(
            @RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> r = new LinkedHashMap<>();
        SystemConfig c = systemService.getConfig();
        r.put("currentRound", c.getCurrentRound());
        r.put("roundOpen", c.getRoundOpen());
        r.put("roundOpenAt", c.getRoundOpenAt());
        r.put("roundCloseAt", c.getRoundCloseAt());
        r.put("now", LocalDateTime.now());
        Long uid = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = uid == null ? null : userRepository.findById(uid).orElse(null);
        Integer grade = u == null ? null : u.getGrade();
        LocalDateTime myOpenAt = staggerService.openAtFor(c.getCurrentRound(), grade);
        boolean staggered = myOpenAt != null;
        boolean myOpen = !staggered || !LocalDateTime.now().isBefore(myOpenAt);
        r.put("grade", grade);
        r.put("staggered", staggered);
        r.put("myOpenAt", myOpenAt);
        r.put("myOpen", myOpen);
        return ApiResponse.ok(r);
    }

    @PostMapping("/round")
    public ApiResponse<SystemConfig> setRound(@RequestBody SystemConfig body) {
        int n = body.getCurrentRound() == null ? 1 : body.getCurrentRound();
        try {
            return ApiResponse.ok("已切换到第 " + n + " 轮", systemService.setRound(n));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/round-open")
    public ApiResponse<SystemConfig> setRoundOpen(@RequestBody SystemConfig body) {
        boolean open = Boolean.TRUE.equals(body.getRoundOpen());
        SystemConfig before = systemService.getConfig();
        int round = before.getCurrentRound() == null ? 1 : before.getCurrentRound();

        // 轮次必须逐轮解锁：第 1 轮需先开启新学期，第 2/3 轮需上一轮已关闭并结算
        if (open) {
            String err = systemService.roundGateError(round, true);
            if (err != null) return ApiResponse.fail(err);
            if (round == 1) systemService.consumeNewSemester();   // 新学期标记只消费一次
        }

        systemService.setRoundOpen(open);
        // 手动切换后补齐本轮时间窗，保证登录页/侧边栏倒计时能拿到目标时间
        SystemConfig c = roundScheduleService.fillCurrentWindow();

        String msg;
        if (open) msg = "第 " + round + " 轮选课已开放";
        else if (round < 3) msg = "第 " + round + " 轮选课已关闭并结算，已解锁第 " + (round + 1) + " 轮";
        else msg = "第 3 轮选课已关闭，本学期三轮选课全部结束";
        if (open && round == 1) msg += "（新学期名额已按学期口径释放，全部报名与成绩记录保留）";
        return ApiResponse.ok(msg, c);
    }

    /**
     * 手动开启新周期：推进学期偏移使名额按学期口径释放，不删除任何报名/成绩记录；
     * 候补与待处理退课申请只标记为 expired 归档。
     */
    @PostMapping("/release-capacity")
    public ApiResponse<Map<String, Object>> releaseCapacity() {
        Map<String, Object> r = capacityService.startNewCycle();
        if (Boolean.TRUE.equals(r.get("alreadyReady"))) {
            return ApiResponse.ok("新学期已开启，无需重复操作；现在可以开启第 1 轮选课", r);
        }
        return ApiResponse.ok("已进入新学期并释放名额：全部 " + r.get("keptEnrollments")
                + " 条报名记录与成绩完整保留，归档 " + r.get("archivedWaitlists") + " 条候补、"
                + r.get("archivedDropRequests") + " 条待处理申请；现在可以开启第 1 轮选课", r);
    }

    @PostMapping("/confirm-minutes")
    public ApiResponse<SystemConfig> setConfirmMinutes(@RequestBody SystemConfig body) {
        int m = body.getConfirmMinutes() != null ? body.getConfirmMinutes() : 30;
        return ApiResponse.ok("候补确认时限已设为 " + m + " 分钟", systemService.setConfirmMinutes(m));
    }

    /** 学期滚动：开启新学期（推进偏移 + 解锁第 1 轮选课），旧成绩与报名按学期归档、不删数据 */
    @PostMapping("/semester-advance")
    public ApiResponse<SystemConfig> advanceSemester() {
        Map<String, Object> r = capacityService.startNewCycle();
        boolean already = Boolean.TRUE.equals(r.get("alreadyReady"));
        String msg = already
                ? "新学期已开启（偏移 " + r.get("semesterOffset") + "），无需重复推进；现在可以开启第 1 轮选课"
                : "新学期已开启（偏移 " + r.get("semesterOffset") + "）：名额按学期口径释放，全部记录保留，现在可以开启第 1 轮选课";
        return ApiResponse.ok(msg, systemService.getConfig());
    }

    /** 教师每学期最多授课门数 */
    @PostMapping("/teacher-max-courses")
    public ApiResponse<SystemConfig> setTeacherMaxCourses(@RequestBody SystemConfig body) {
        return ApiResponse.ok("教师每学期授课上限已设为 " + systemService.setTeacherMaxCourses(body.getTeacherMaxCourses()).getTeacherMaxCourses() + " 门", systemService.getConfig());
    }

    /** 每学期学分上限 */
    @PostMapping("/max-credit")
    public ApiResponse<SystemConfig> setMaxCredit(@RequestBody SystemConfig body) {
        int v = body.getMaxCreditPerSemester() != null ? body.getMaxCreditPerSemester() : 24;
        return ApiResponse.ok("每学期学分上限已设为 " + v, systemService.setMaxCredit(v));
    }

    /** 每学期选课门数上限 */
    @PostMapping("/max-courses")
    public ApiResponse<SystemConfig> setMaxCourses(@RequestBody SystemConfig body) {
        int v = body.getMaxCoursesPerSemester() != null ? body.getMaxCoursesPerSemester() : 2;
        return ApiResponse.ok("每学期选课门数上限已设为 " + v, systemService.setMaxCourses(v));
    }

    /** 补考/重修参数（封顶分、次数、组班门槛、报名窗口） */
    @PostMapping("/makeup-config")
    public ApiResponse<SystemConfig> setMakeupConfig(@RequestBody Map<String, Object> body) {
        Integer cap = intOrNull(body.get("makeupScoreCap"));
        Integer makeupMax = intOrNull(body.get("makeupMaxAttempts"));
        Integer retakeMax = intOrNull(body.get("maxRetakeAttempts"));
        Integer threshold = intOrNull(body.get("retakeGroupThreshold"));
        LocalDateTime openAt = timeOrNull(body.get("makeupOpenAt"));
        LocalDateTime closeAt = timeOrNull(body.get("makeupCloseAt"));
        SystemConfig c = systemService.setMakeupConfig(cap, makeupMax, retakeMax, threshold, openAt, closeAt);
        return ApiResponse.ok("补考/重修参数已保存", c);
    }

    private static Integer intOrNull(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try { return Integer.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** 支持 "2026-12-01T09:00" 或 "2026-12-01 09:00"；空串表示清除 */
    private static LocalDateTime timeOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        return LocalDateTime.parse(s.replace(' ', 'T'));
    }

    @PostMapping("/graduation-reqs")
    public ApiResponse<SystemConfig> setGraduationReqs(@RequestBody SystemConfig body) {
        SystemConfig c = systemService.setGraduationReqs(
                body.getGradReqRequired(), body.getGradReqElective(),
                body.getGradReqGeneral(), body.getGradReqPractice(), body.getGradReqCourses());
        return ApiResponse.ok("毕业要求已更新", c);
    }

    /* ---------- 按年级差异化毕业要求 ---------- */

    @GetMapping("/grade-reqs")
    public ApiResponse<?> gradeReqs() {
        return ApiResponse.ok(systemService.listGradeReqs());
    }

    /** 整表保存：以提交列表为准全量替换 */
    @PostMapping("/grade-reqs")
    public ApiResponse<Void> saveGradeReqs(@RequestBody Map<String, Object> body) {
        Object itemsObj = body.get("items");
        java.util.List<GradeRequirement> items = new java.util.ArrayList<>();
        if (itemsObj instanceof List<?> list) {
            // 列表行可能附带供给核对等展示字段（planBxMin/warnings…），反序列化需忽略未知键
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            for (Object o : list) {
                try { items.add(om.convertValue(o, GradeRequirement.class)); } catch (Exception ignored) {}
            }
        }
        Map<String, Object> r = systemService.saveGradeReqs(items);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    @DeleteMapping("/grade-reqs/{id}")
    public ApiResponse<Void> deleteGradeReq(@PathVariable Long id) {
        Map<String, Object> r = systemService.deleteGradeReq(id);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/phase")
    public ApiResponse<SystemConfig> setPhase(@RequestBody SystemConfig body) {
        SystemConfig cc = systemService.setPhase(body);
        return ApiResponse.ok("阶段设置已更新", cc);
    }

    @GetMapping("/phase")
    public ApiResponse<Map<String, Object>> getPhase() {
        SystemConfig cfg = systemService.getConfig();
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String phase = cfg.getCurrentPhase();
        if (phase == null || phase.isEmpty() || "auto".equals(phase)) {
            if (cfg.getReselectOpenAt() != null && !now.isBefore(cfg.getReselectOpenAt())
                    && (cfg.getReselectCloseAt() == null || now.isBefore(cfg.getReselectCloseAt()))) {
                phase = "reselect";
            } else if (cfg.getBatchOpenAt() != null && !now.isBefore(cfg.getBatchOpenAt())
                    && (cfg.getBatchCloseAt() == null || now.isBefore(cfg.getBatchCloseAt()))) {
                phase = "batch";
            } else if (cfg.getRoundOpenAt() != null && !now.isBefore(cfg.getRoundOpenAt())
                    && (cfg.getRoundCloseAt() == null || now.isBefore(cfg.getRoundCloseAt()))) {
                phase = "regular";
            } else {
                phase = "closed";
            }
        }
        r.put("currentPhase", phase);
        r.put("roundOpenAt", cfg.getRoundOpenAt());
        r.put("roundCloseAt", cfg.getRoundCloseAt());
        r.put("batchOpenAt", cfg.getBatchOpenAt());
        r.put("batchCloseAt", cfg.getBatchCloseAt());
        r.put("reselectOpenAt", cfg.getReselectOpenAt());
        r.put("reselectCloseAt", cfg.getReselectCloseAt());
        r.put("roundOpen", cfg.getRoundOpen());
        r.put("maxCreditPerSemester", cfg.getMaxCreditPerSemester());
        r.put("gradReqRequired", cfg.getGradReqRequired());
        r.put("gradReqElective", cfg.getGradReqElective());
        r.put("gradReqGeneral", cfg.getGradReqGeneral());
        r.put("gradReqPractice", cfg.getGradReqPractice());
        r.put("gradReqCourses", cfg.getGradReqCourses());
        return ApiResponse.ok(r);
    }
}
