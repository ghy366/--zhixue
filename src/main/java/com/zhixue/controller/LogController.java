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

    /**
     * 操作日志列表（无限增长表，服务端分页）。传 page/size 时返回 {items,total,page,size}，
     * 未传时保持旧的全量数组（CSV 导出等旧调用方零破坏）。type/status/keyword 服务端过滤，
     * keyword 匹配 用户名/操作内容/IP（与前端原本地筛选口径一致）。
     */
    @GetMapping
    public ApiResponse<Object> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<OperationLog> all = logService.findAll();
        if (type != null && !type.isBlank()) {
            all = all.stream().filter(l -> type.equals(l.getType())).toList();
        }
        if (status != null && !status.isBlank()) {
            all = all.stream().filter(l -> status.equals(l.getStatus())).toList();
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            all = all.stream().filter(l -> (str(l.getUserName()) + " " + str(l.getOperation()) + " " + str(l.getIp()))
                    .toLowerCase().contains(k)).toList();
        }
        if (page == null || size == null || page < 1 || size < 1) {
            return ApiResponse.ok(all);   // 兼容模式
        }
        int from = Math.min(all.size(), (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", all.subList(from, to));
        data.put("total", all.size());
        data.put("page", page);
        data.put("size", size);
        return ApiResponse.ok(data);
    }

    private String str(Object v) { return v == null ? "" : v.toString(); }

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

    /** 操作时间热力：星期 × 24 小时 */
    @GetMapping("/heatmap")
    public ApiResponse<Map<String, Object>> heatmap() {
        return ApiResponse.ok(logService.heatmap());
    }
}
