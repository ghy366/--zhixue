package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.TeachingClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 教学班（教务端）：重修一键组班、排课、停开与名单。
 */
@RestController
@RequestMapping("/api/teaching-classes")
public class TeachingClassController {

    @Autowired private TeachingClassService service;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long courseId,
                                                       @RequestParam(required = false) Integer semesterNo,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String source,
                                                       @RequestParam(required = false) Long teacherId) {
        return ApiResponse.ok(service.listClasses(courseId, semesterNo, status, source, teacherId));
    }

    /** 一键组班：建班 + 排课 + 学生进班并写入课表 */
    @PostMapping("/group")
    public ApiResponse<Map<String, Object>> group(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> r = service.groupRetakes(body);
            return ApiResponse.ok(String.valueOf(r.get("message")), r);
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 排课：改上课时间/地点/教师/容量 */
    @PutMapping("/{id}/schedule")
    public ApiResponse<Map<String, Object>> schedule(@PathVariable Long id,
                                                     @RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.ok("排课已保存，学生课表已同步", service.scheduleClass(id, body));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PutMapping("/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id) {
        try {
            Map<String, Object> r = service.closeClass(id);
            return ApiResponse.ok("教学班已停开，退回 " + r.get("released") + " 人", r);
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable Long id) {
        try {
            return ApiResponse.ok(service.members(id));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
