package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.SystemConfig;
import com.zhixue.service.SystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Autowired private SystemService systemService;

    @GetMapping("/config")
    public ApiResponse<SystemConfig> config() {
        return ApiResponse.ok(systemService.getConfig());
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
