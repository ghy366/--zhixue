package com.zhixue.service;

import com.zhixue.entity.StaggerWindow;
import com.zhixue.repository.StaggerWindowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 错峰开放服务：维护「轮次 + 年级 → 开放时间」配置，
 * 并提供报名前的错峰校验（仅第 1、2 轮生效）。
 */
@Service
public class StaggerService {

    @Autowired private StaggerWindowRepository repo;

    /** 全部错峰窗口，按轮次、年级排序 */
    public List<StaggerWindow> listAll() {
        List<StaggerWindow> all = new ArrayList<>(repo.findAll());
        all.sort(Comparator.comparing(StaggerWindow::getRoundNo)
                .thenComparing(StaggerWindow::getGrade));
        return all;
    }

    /** 全量覆盖保存（第 3 轮不参与错峰，忽略 roundNo>=3 的条目） */
    @Transactional
    public List<StaggerWindow> saveAll(List<StaggerWindow> items) {
        // 注意：必须先 flush 删除，否则 Hibernate 会先 insert 再 delete，
        // 与 (round_no, grade) 唯一键冲突，导致“第二次保存错峰配置”直接 500。
        repo.deleteAll();
        repo.flush();
        List<StaggerWindow> clean = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (items != null) {
            for (StaggerWindow in : items) {
                if (in == null || in.getRoundNo() == null || in.getGrade() == null || in.getOpenAt() == null) continue;
                if (in.getRoundNo() >= 3) continue; // 第三轮先到先得，不参与错峰
                String key = in.getRoundNo() + ":" + in.getGrade();
                if (!seen.add(key)) continue;
                clean.add(new StaggerWindow(in.getRoundNo(), in.getGrade(), in.getOpenAt()));
            }
        }
        repo.saveAll(clean);
        return listAll();
    }

    /**
     * 学生错峰校验：返回 null 表示可报名；否则返回提示文案。
     * 规则：仅第 1、2 轮；学生无年级或未配置该年级窗口 → 跟随全局开放，直接放行。
     */
    public String checkGate(Integer round, Integer grade) {
        if (round == null || round >= 3 || grade == null) return null;
        StaggerWindow w = repo.findByRoundNoAndGrade(round, grade).orElse(null);
        if (w == null || w.getOpenAt() == null) return null;
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(w.getOpenAt())) return null;
        return "你所在年级（" + grade + " 级）的选课将于 "
                + w.getOpenAt().toString().replace('T', ' ').substring(0, 16)
                + " 开放，请到时再来";
    }

    /** 某年级在某轮的开放时间（无配置返回 null） */
    public LocalDateTime openAtFor(Integer round, Integer grade) {
        if (round == null || grade == null) return null;
        return repo.findByRoundNoAndGrade(round, grade).map(StaggerWindow::getOpenAt).orElse(null);
    }
}
