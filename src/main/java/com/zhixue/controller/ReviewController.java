package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.service.ReviewService;
import com.zhixue.util.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 课程评价与评分 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired private ReviewService reviewService;

    private Long uid(String token) {
        return token == null ? null : SessionStore.getUserId(token.replace("Bearer ", ""));
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<Map<String, Object>> courseReviews(@PathVariable Long courseId,
                                                          @RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(reviewService.courseReviews(courseId, uid(token)));
    }

    @GetMapping("/ratings")
    public ApiResponse<Map<String, Object>> allRatings() {
        return ApiResponse.ok(reviewService.allRatings());
    }

    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(reviewService.mine(uid(token)));
    }

    @GetMapping("/pending")
    public ApiResponse<List<Map<String, Object>>> pending(@RequestHeader(value = "Authorization", required = false) String token) {
        return ApiResponse.ok(reviewService.pending(uid(token)));
    }

    @GetMapping("/teacher/{teacherId}")
    public ApiResponse<Map<String, Object>> teacherSummary(@PathVariable Long teacherId) {
        return ApiResponse.ok(reviewService.teacherSummary(teacherId));
    }

    @PostMapping
    public ApiResponse<Void> submit(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "Authorization", required = false) String token) {
        Long courseId = Long.valueOf(body.get("courseId").toString());
        Integer rating = body.get("rating") == null ? null : Integer.parseInt(body.get("rating").toString());
        String content = body.get("content") == null ? null : body.get("content").toString();
        Integer teaching = body.get("teaching") == null ? null : Integer.parseInt(body.get("teaching").toString());
        Integer contentScore = body.get("contentScore") == null ? null : Integer.parseInt(body.get("contentScore").toString());
        Integer homework = body.get("homework") == null ? null : Integer.parseInt(body.get("homework").toString());
        Boolean anonymous = body.get("anonymous") == null ? null : Boolean.valueOf(body.get("anonymous").toString());
        Map<String, Object> r = reviewService.submit(uid(token), courseId, rating, content, teaching, contentScore, homework, anonymous);
        return Boolean.TRUE.equals(r.get("success"))
                ? ApiResponse.ok((String) r.get("msg"), null)
                : ApiResponse.fail((String) r.get("msg"));
    }
}
