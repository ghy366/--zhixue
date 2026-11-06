package com.zhixue.service;

import com.zhixue.entity.OperationLog;
import com.zhixue.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogService {

    @Autowired private OperationLogRepository logRepo;

    public void log(Long userId, String userName, String operation, String type, String status, String ip) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setUserName(userName);
        log.setOperation(operation);
        log.setType(type);
        log.setStatus(status);
        log.setIp(ip);
        log.setCreatedAt(LocalDateTime.now());
        logRepo.save(log);
    }

    public List<OperationLog> findAll() {
        return logRepo.findAllByOrderByCreatedAtDesc();
    }

    public List<Object[]> countByType() {
        return logRepo.countByType();
    }

    public List<Object[]> countByStatus() {
        return logRepo.countByStatus();
    }

    public List<Object[]> countByUser() {
        return logRepo.countByUser();
    }

    public List<OperationLog> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return logRepo.findByCreatedAtBetweenOrderByCreatedAtAsc(start, end);
    }

    /** 操作时间热力：星期 × 24 小时矩阵，看用户集中在什么时段操作 */
    public Map<String, Object> heatmap() {
        List<OperationLog> all = logRepo.findAll();
        int[][] cells = new int[7][24];
        int max = 0, peakDay = -1, peakHour = -1;
        for (OperationLog l : all) {
            if (l.getCreatedAt() == null) continue;
            int d = l.getCreatedAt().getDayOfWeek().getValue() - 1;   // 周一=0
            int h = l.getCreatedAt().getHour();
            cells[d][h]++;
            if (cells[d][h] > max) { max = cells[d][h]; peakDay = d; peakHour = h; }
        }
        List<List<Integer>> rows = new ArrayList<>();
        for (int[] row : cells) {
            List<Integer> r = new ArrayList<>();
            for (int v : row) r.add(v);
            rows.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("max", max);
        out.put("total", all.size());
        out.put("peakDay", peakDay < 0 ? null : new String[]{"周一", "周二", "周三", "周四", "周五", "周六", "周日"}[peakDay]);
        out.put("peakHour", peakHour);
        out.put("peakCount", max);
        return out;
    }
}
