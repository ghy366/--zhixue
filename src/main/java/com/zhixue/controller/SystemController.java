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
        return ApiResponse.ok("已切换到第 " + body.getCurrentRound() + " 轮", systemService.setRound(body.getCurrentRound()));
    }

    @PostMapping("/round-open")
    public ApiResponse<SystemConfig> setRoundOpen(@RequestBody SystemConfig body) {
        boolean open = Boolean.TRUE.equals(body.getRoundOpen());
        SystemConfig before = systemService.getConfig();
        boolean wasOpen = Boolean.TRUE.equals(before.getRoundOpen());
        int round = before.getCurrentRound() == null ? 1 : before.getCurrentRound();

        // 第 1 轮开启 = 新选课周期开始：先把上一周期残留的报名/候补占位清空，
        // 让全部课程容量重新满额（成绩 grades 不动，学分/GPA 不受影响）
        Map<String, Object> released = null;
        if (open && !wasOpen && round == 1) {
            try { released = capacityService.releaseAll(); } catch (Exception ignored) {}
        }

        systemService.setRoundOpen(open);
        // 手动切换后补齐本轮时间窗，保证登录页/侧边栏倒计时能拿到目标时间
        SystemConfig c = roundScheduleService.fillCurrentWindow();

        String msg = open ? "本轮选课已开放" : "本轮选课已关闭";
        if (released != null) {
            msg += "，并释放本学期容量（清理 " + released.get("enrollments") + " 条本学期报名，让出 "
                    + released.get("freedSeats") + " 个已占座位；历史学期 " + released.get("keptHistory") + " 条报名记录已保留）";
        }
        return ApiResponse.ok(msg, c);
    }

    /**
     * 手动释放本学期容量：清空本学期报名 / 候补 / 待处理退课申请，
     * 历史学期的报名记录与成绩 grades 保持不变。用于新学期开轮前重置。
     */
    @PostMapping("/release-capacity")
    public ApiResponse<Map<String, Object>> releaseCapacity() {
        Map<String, Object> r = capacityService.releaseAll();
        return ApiResponse.ok("已释放本学期容量：清理 " + r.get("enrollments") + " 条本学期报名、"
                + r.get("waitlists") + " 条候补、" + r.get("dropRequests") + " 条待处理退课申请，让出 "
                + r.get("freedSeats") + " 个已占座位；历史学期 " + r.get("keptHistory") + " 条报名记录已保留", r);
    }

    @PostMapping("/confirm-minutes")
    public ApiResponse<SystemConfig> setConfirmMinutes(@RequestBody SystemConfig body) {
        int m = body.getConfirmMinutes() != null ? body.getConfirmMinutes() : 30;
        return ApiResponse.ok("候补确认时限已设为 " + m + " 分钟", systemService.setConfirmMinutes(m));
    }

    /** 学期滚动：推进到下一学期（旧学期成绩归档、新学期必修自动排入） */
    @PostMapping("/semester-advance")
    public ApiResponse<SystemConfig> advanceSemester() {
        SystemConfig c = systemService.getConfig();
        int off = c.getSemesterOffset() == null ? 0 : c.getSemesterOffset();
        systemService.setSemesterOffset(off + 1);
        return ApiResponse.ok("已推进到下一学期（偏移 " + (off + 1) + "），新学期必修课将自动排入", systemService.getConfig());
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
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
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
