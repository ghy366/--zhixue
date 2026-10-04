package com.zhixue.service;

import com.zhixue.entity.Semester;
import com.zhixue.repository.SemesterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SemesterService {

    @Autowired private SemesterRepository semesterRepo;

    public List<Semester> findAll() {
        return semesterRepo.findAll();
    }

    public Semester findCurrent() {
        return semesterRepo.findByIsCurrentTrue().orElse(null);
    }

    public Semester save(Semester s) {
        if (Boolean.TRUE.equals(s.getIsCurrent())) {
            semesterRepo.findAll().forEach(existing -> {
                if (!existing.getId().equals(s.getId())) {
                    existing.setIsCurrent(false);
                    semesterRepo.save(existing);
                }
            });
        }
        return semesterRepo.save(s);
    }

    public void delete(Long id) {
        semesterRepo.deleteById(id);
    }

    public String computeStatus(Semester s) {
        LocalDate now = LocalDate.now();
        if (s.getSelectionStart() != null && now.isBefore(s.getSelectionStart())) return "未开始";
        if (s.getSelectionEnd() != null && now.isAfter(s.getSelectionEnd())) return "已结束";
        return "进行中";
    }
}
