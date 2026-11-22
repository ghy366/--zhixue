package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.MakeupRetakeService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 重修：学生报名与跟班选班（/mine、/eligible、/classes），教务定形式与汇总。
 */
@RestController
@RequestMapping("/api/retake")
public class RetakeController {

    @Autowired private MakeupRetakeService service;

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        return ApiResponse.ok(service.myRetakes(userId));
    }

    /** 可报名重修清单（与 /mine 同源，单独保留便于前端语义区分） */
    @GetMapping("/eligible")
    public ApiResponse<List<Map<String, Object>>> eligible(@RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        return ApiResponse.ok(service.myRetakes(userId));
    }

    @PostMapping("/{id}/apply")
    public ApiResponse<Map<String, Object>> apply(@PathVariable Long id,
                                                  @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        try {
            return ApiResponse.ok("重修报名成功，请等待教务安排教学班", service.applyRetake(userId, id));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 跟班重修可选教学班（同课程名，带与当前课表的时间冲突标记） */
    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes(@RequestParam Long courseId,
                                                          @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        try {
            return ApiResponse.ok(service.classOptions(userId, courseId));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/choose-class")
    public ApiResponse<Map<String, Object>> chooseClass(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body,
                                                        @RequestHeader("Authorization") String token) {
        Long userId = SessionStore.getUserId(token.replace("Bearer ", ""));
        if (userId == null) return ApiResponse.fail("登录已过期，请重新登录");
        Object cid = body.get("courseId") != null ? body.get("courseId") : body.get("targetClassId");
        if (cid == null) return ApiResponse.fail("请选择教学班");
        try {
            Map<String, Object> r = service.chooseClass(userId, id, Long.valueOf(cid.toString()));
            return ApiResponse.ok("已加入跟班重修教学班，课程已写入课表", r);
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String form,
                                                       @RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(service.listRetakes(status, form, courseId));
    }

    @PutMapping("/{id}/form")
    public ApiResponse<Map<String, Object>> setForm(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        try {
            String form = body.get("form") == null ? null : body.get("form").toString();
            Long targetClassId = body.get("targetClassId") == null ? null
                    : Long.valueOf(body.get("targetClassId").toString());
            return ApiResponse.ok("重修形式已设置", service.setRetakeForm(id, form, targetClassId));
        } catch (IllegalStateException e) {
            return ApiResponse.fail(e.getMessage());
        }
    }

    /** 报名汇总：课程 × 形式（组班门槛判定） */
    @GetMapping("/summary")
    public ApiResponse<List<Map<String, Object>>> summary() {
        return ApiResponse.ok(service.retakeSummary());
    }
}
