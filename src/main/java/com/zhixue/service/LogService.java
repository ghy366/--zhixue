package com.zhixue.service;

import com.zhixue.entity.OperationLog;
import com.zhixue.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
}
