package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.IntentService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 预选意向单 */
@RestController
@RequestMapping("/api/intents")
public class IntentController {

    @Autowired private IntentService intentService;

    private Long uid(String token) {
        return token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(intentService.list(uid(token)));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody Map<String, Object> body,
                                 @RequestHeader(value = "Authorization", required = false) String token) {
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Integer points = body.get("points") == null || body.get("points").toString().isEmpty()
                ? null : Integer.parseInt(body.get("points").toString());
        String note = body.get("note") == null ? null : body.get("note").toString();
        Map<String, Object> r = intentService.add(uid(token), courseId, points, note);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/remove")
    public ApiResponse<Void> remove(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Authorization", required = false) String token) {
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Map<String, Object> r = intentService.remove(uid(token), courseId);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/clear")
    public ApiResponse<Void> clear(@RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> r = intentService.clear(uid(token));
        return ApiResponse.ok((String) r.get("msg"), null);
    }

    @PostMapping("/convert")
    public ApiResponse<Map<String, Object>> convert(@RequestBody Map<String, Object> body,
                                                    @RequestHeader(value = "Authorization", required = false) String token) {
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Map<String, Object> r = intentService.convertOne(uid(token), courseId);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }

    @PostMapping("/submit")
    public ApiResponse<Map<String, Object>> submit(@RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> r = intentService.submitAll(uid(token));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.get("data");
        return ApiResponse.ok((String) r.get("msg"), data);
    }
}
