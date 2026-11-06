package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.User;
import com.zhixue.repository.UserRepository;
import com.zhixue.service.ExcelService;
import com.zhixue.service.StatsService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 管理端统计报表 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired private StatsService statsService;
    @Autowired private UserRepository userRepo;
    @Autowired private ExcelService excelService;

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @GetMapping("/report")
    public ApiResponse<Map<String, Object>> report(@RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.report());
    }

    /** 积分分析：选修课投分箱线图 + 单课投分分布 */
    @GetMapping("/points")
    public ApiResponse<Map<String, Object>> points(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "round", required = false) Integer round) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.pointsAnalysis(round));
    }

    /** 候补分析：漏斗 + 队列 + 等待时长 */
    @GetMapping("/waitlist")
    public ApiResponse<Map<String, Object>> waitlist(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "round", required = false) Integer round) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.waitlistAnalysis(round));
    }

    /** 教室/排课分析：星期 × 时段热力 + 地点排行 */
    @GetMapping("/classroom")
    public ApiResponse<Map<String, Object>> classroom(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.classroomAnalysis());
    }

    /** 成绩分析：总分分布 + 课程均分对比 + 平时/期末相关性 */
    @GetMapping("/grades")
    public ApiResponse<Map<String, Object>> grades(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.gradeAnalysis());
    }

    /** 意向分析：在册意向 → 报名/录取流转 + 投分偏差 */
    @GetMapping("/intents")
    public ApiResponse<Map<String, Object>> intents(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "round", required = false) Integer round) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.intentAnalysis(round));
    }

    /** 实时监控：操作节奏 + 报名赛跑 + 最新动态（前端轮询） */
    @GetMapping("/live")
    public ApiResponse<Map<String, Object>> live(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.liveAnalysis());
    }

    /** 评价分析：星级分布 + 评分 vs 热度 + 评论词云 */
    @GetMapping("/reviews")
    public ApiResponse<Map<String, Object>> reviews(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.reviewAnalysis());
    }

    /** 公平性分析：录取率、同分抽签、学生录取分布基尼、年级均衡 */
    @GetMapping("/fairness")
    public ApiResponse<Map<String, Object>> fairness(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "round", required = false) Integer round) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.fairnessAnalysis(round));
    }

    /** 性能风控：QPS 节奏、失败率、异常高频账号、拥挤课程与告警 */
    @GetMapping("/risk")
    public ApiResponse<Map<String, Object>> risk(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.riskAnalysis());
    }

    /** 运营分析：供需缺口、名额余量热力、退课统计、毕业预警 */
    @GetMapping("/operation")
    public ApiResponse<Map<String, Object>> operation(
            @RequestHeader(value = "Authorization", required = false) String token) {
        if (!isAdmin(token)) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.operationAnalysis());
    }

    private boolean isAdmin(String token) {
        Long userId = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        return u != null && "admin".equals(u.getRole());
    }

    @GetMapping("/export/xlsx")
    public ResponseEntity<byte[]> exportXlsx(@RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        if (u == null || !"admin".equals(u.getRole()))
            return ResponseEntity.status(403).body(null);
        Map<String, Object> rep = statsService.report();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hot = (List<Map<String, Object>>) rep.get("hot");
        List<List<String>> rows = new ArrayList<>();
        rows.add(java.util.Arrays.asList("课程", "教师", "类别", "容量", "报名", "录取", "候补", "满员率(%)", "状态"));
        for (Map<String, Object> m : hot) {
            int applied = (int) m.getOrDefault("applied", 0);
            int waiting = (int) m.getOrDefault("waiting", 0);
            double fillRate = (double) m.getOrDefault("fillRate", 0.0);
            String status = fillRate >= 100 && waiting > 0 ? "爆满·有候补"
                    : fillRate >= 100 ? "满员" : applied == 0 ? "冷门·零报名" : "正常";
            rows.add(java.util.Arrays.asList(
                    String.valueOf(m.get("courseName")), String.valueOf(m.get("teacherName")),
                    String.valueOf(m.get("category")), String.valueOf(m.get("capacity")),
                    String.valueOf(applied), String.valueOf(m.get("approved")),
                    String.valueOf(waiting), String.valueOf(fillRate), status));
        }
        byte[] bytes = excelService.toXlsx("课程热度分析", rows);
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"course-stats.xlsx\"");
        h.set(HttpHeaders.CONTENT_TYPE, XLSX);
        return new ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }
}
