package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.MakeupRetakeService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 补考：学生报名（/mine、/{id}/register），教务管理册子（生成、排考、弃考、导出）。
 */
@RestController
@RequestMapping("/api/makeup")
public class MakeupController {

    @Autowired private MakeupRetakeService service;

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        return ApiResponse.ok(service.myMakeups(userId));
    }

    @PostMapping("/{id}/register")
    public ApiResponse<Map<String, Object>> register(@PathVariable Long id,
                                                     @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        try {
            return ApiResponse.ok("补考报名成功", service.registerMakeup(userId, id));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Long courseId,
                                                       @RequestParam(required = false) Integer semesterNo) {
        return ApiResponse.ok(service.listMakeups(status, courseId, semesterNo));
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestBody(required = false) Map<String, Object> body) {
        Integer semesterNo = body != null && body.get("semesterNo") != null
                ? Integer.valueOf(body.get("semesterNo").toString()) : null;
        String semesterLabel = body != null && body.get("semesterLabel") != null
                ? body.get("semesterLabel").toString() : null;
        Map<String, Object> r = service.generateMakeups(semesterNo, semesterLabel);
        return ApiResponse.ok("补考册已生成：新增 " + r.get("created") + " 条，已存在 " + r.get("skipped") + " 条", r);
    }

    @PutMapping("/{id}/schedule")
    public ApiResponse<Map<String, Object>> schedule(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        try {
            String examTime = body.get("examTime") == null ? null : body.get("examTime").toString();
            String location = body.get("location") == null ? null : body.get("location").toString();
            String invigilator = body.get("invigilator") == null ? null : body.get("invigilator").toString();
            return ApiResponse.ok("排考已保存", service.scheduleMakeup(id, examTime, location, invigilator));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}/absent")
    public ApiResponse<Map<String, Object>> absent(@PathVariable Long id) {
        try {
            return ApiResponse.ok("已标记弃考，系统已生成重修申请", service.absentMakeup(id));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Integer semesterNo,
                                         @RequestParam(required = false) String semesterLabel) {
        byte[] bytes = service.exportMakeups(semesterNo, semesterLabel);
        HttpHeaders h = new HttpHeaders();
        String encoded = java.net.URLEncoder.encode("补考名册.xlsx", java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        h.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"makeup-roster.xlsx\"; filename*=UTF-8''" + encoded);
        h.set(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return new ResponseEntity<>(bytes, h, org.springframework.http.HttpStatus.OK);
    }
}
