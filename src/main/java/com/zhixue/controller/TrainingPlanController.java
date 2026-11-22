package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.TrainingPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 培养方案管理（教务端）：按专业+年级+学期维护必修课清单，一键批量排定。
 */
@RestController
@RequestMapping("/api/training-plan")
public class TrainingPlanController {

    @Autowired private TrainingPlanService planService;

    /** 全量方案条目（管理页表格） */
    @GetMapping("/items")
    public ApiResponse<Object> items(@RequestParam(required = false) String major) {
        return ApiResponse.ok(major == null || major.isBlank()
                ? planService.listAll() : planService.listByMajor(major));
    }

    /** 概览统计 */
    @GetMapping("/stats")
    public ApiResponse<Object> stats() {
        return ApiResponse.ok(planService.stats());
    }

    /** 批量排定影响面预览（按专业+年级分组，不写库） */
    @GetMapping("/preview")
    public ApiResponse<Object> preview() {
        return ApiResponse.ok(planService.preview());
    }

    /** 教务端一键批量排定：全校学生按各自培养方案补齐必修 */
    @PostMapping("/batch-ensure")
    public ApiResponse<Object> batchEnsure() {
        Map<String, Object> r = planService.batchEnsure();
        String msg = "已处理 " + r.get("processed") + " 名学生，新增 " + r.get("added") + " 条必修记录"
                + (Integer.valueOf(0).equals(r.get("failed")) ? "" : "，失败 " + r.get("failed") + " 名");
        return ApiResponse.ok(msg, r);
    }

    /** 单条新增 */
    @PostMapping("/items")
    public ApiResponse<Object> add(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = planService.add(body);
        return Boolean.TRUE.equals(r.get("success")) ? ApiResponse.ok(r) : ApiResponse.fail((String) r.get("message"));
    }

    /** 单条修改 */
    @PutMapping("/items/{id}")
    public ApiResponse<Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = planService.update(id, body);
        return Boolean.TRUE.equals(r.get("success")) ? ApiResponse.ok(r) : ApiResponse.fail((String) r.get("message"));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        Map<String, Object> r = planService.delete(id);
        return Boolean.TRUE.equals(r.get("success")) ? ApiResponse.ok(r) : ApiResponse.fail((String) r.get("message"));
    }

    /** 整段替换某专业+年级方案 */
    @PutMapping("/replace")
    public ApiResponse<Object> replace(@RequestBody Map<String, Object> body) {
        String major = String.valueOf(body.getOrDefault("major", "")).trim();
        Object g = body.get("grade");
        Integer grade = null;
        try { if (g != null && !String.valueOf(g).isBlank()) grade = Integer.valueOf(String.valueOf(g)); } catch (Exception ignored) {}
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> r = planService.replace(major, grade, items == null ? List.of() : items);
        return Boolean.TRUE.equals(r.get("success")) ? ApiResponse.ok(r) : ApiResponse.fail((String) r.get("message"));
    }
}
