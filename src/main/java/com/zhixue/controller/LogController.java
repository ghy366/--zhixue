package com.zhixue.controller;

import com.zhixue.dto.ApiResponse;
import com.zhixue.entity.OperationLog;
import com.zhixue.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    @Autowired private LogService logService;

    @GetMapping
    public ApiResponse<List<OperationLog>> list() {
        return ApiResponse.ok(logService.findAll());
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> result = new HashMap<>();

        // 按类型统计（饼图）
        List<Object[]> byType = logService.countByType();
        Map<String, Long> typeMap = new LinkedHashMap<>();
        for (Object[] row : byType) {
            typeMap.put((String) row[0], ((Number) row[1]).longValue());
        }
        result.put("byType", typeMap);

        // 按状态统计
        List<Object[]> byStatus = logService.countByStatus();
        Map<String, Long> statusMap = new LinkedHashMap<>();
        for (Object[] row : byStatus) {
            statusMap.put((String) row[0], ((Number) row[1]).longValue());
        }
        result.put("byStatus", statusMap);

        // 用户操作排行（柱状图）
        List<Object[]> byUser = logService.countByUser();
        List<Map<String, Object>> userList = new ArrayList<>();
        for (Object[] row : byUser) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userName", row[0]);
            m.put("count", ((Number) row[1]).longValue());
            userList.add(m);
        }
        result.put("byUser", userList);

        return ApiResponse.ok(result);
    }
}
