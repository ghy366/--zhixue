package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.RoundSchedule;
import com.zhixue.entity.SystemConfig;
import com.zhixue.service.RoundScheduleService;
import com.zhixue.service.SystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired private SystemService systemService;
    @Autowired private RoundScheduleService roundScheduleService;

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

    @PostMapping("/round")
    public ApiResponse<SystemConfig> setRound(@RequestBody SystemConfig body) {
        return ApiResponse.ok("已切换到第 " + body.getCurrentRound() + " 轮", systemService.setRound(body.getCurrentRound()));
    }

    @PostMapping("/round-open")
    public ApiResponse<SystemConfig> setRoundOpen(@RequestBody SystemConfig body) {
        SystemConfig c = systemService.setRoundOpen(body.getRoundOpen());
        return ApiResponse.ok(body.getRoundOpen() ? "本轮选课已开放" : "本轮选课已关闭", c);
    }

    @PostMapping("/confirm-minutes")
    public ApiResponse<SystemConfig> setConfirmMinutes(@RequestBody SystemConfig body) {
        int m = body.getConfirmMinutes() != null ? body.getConfirmMinutes() : 30;
        return ApiResponse.ok("候补确认时限已设为 " + m + " 分钟", systemService.setConfirmMinutes(m));
    }

    @PostMapping("/graduation-reqs")
    public ApiResponse<SystemConfig> setGraduationReqs(@RequestBody SystemConfig body) {
        SystemConfig c = systemService.setGraduationReqs(
                body.getGradReqRequired(), body.getGradReqElective(),
                body.getGradReqGeneral(), body.getGradReqPractice());
        return ApiResponse.ok("毕业学分要求已更新", c);
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
        return ApiResponse.ok(r);
    }
}
