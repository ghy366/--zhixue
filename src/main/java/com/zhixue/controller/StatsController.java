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
        Long userId = token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
        User u = userId == null ? null : userRepo.findById(userId).orElse(null);
        if (u == null || !"admin".equals(u.getRole())) return ApiResponse.fail("仅管理员可查看统计报表");
        return ApiResponse.ok(statsService.report());
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
