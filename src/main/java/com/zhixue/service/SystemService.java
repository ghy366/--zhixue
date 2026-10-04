package com.zhixue.service;

import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SystemService {

    @Autowired private SystemConfigRepository sysRepo;
    @Autowired private EnrollmentService enrollmentService;

    public SystemConfig getConfig() { return enrollmentService.getSystemConfig(); }

    public SystemConfig setRound(int round) {
        SystemConfig c = getConfig();
        c.setCurrentRound(round);
        return sysRepo.save(c);
    }

    public SystemConfig setRoundOpen(boolean open) {
        SystemConfig c = getConfig();
        c.setRoundOpen(open);
        return sysRepo.save(c);
    }

    public SystemConfig setConfirmMinutes(int minutes) {
        SystemConfig c = getConfig();
        c.setConfirmMinutes(minutes);
        return sysRepo.save(c);
    }

    public SystemConfig setGraduationReqs(Integer required, Integer elective, Integer general, Integer practice) {
        SystemConfig c = getConfig();
        if (required != null) c.setGradReqRequired(required);
        if (elective != null) c.setGradReqElective(elective);
        if (general != null) c.setGradReqGeneral(general);
        if (practice != null) c.setGradReqPractice(practice);
        return sysRepo.save(c);
    }
}
