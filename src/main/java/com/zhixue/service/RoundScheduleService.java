package com.zhixue.service;

import com.zhixue.entity.RoundSchedule;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.RoundScheduleRepository;
import com.zhixue.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 选课轮次时间窗口：为第 1/2/3 轮分别预设「开始时间 ~ 结束时间」。
 * 到开始时间自动开放选课，到结束时间自动关闭，无需人工切换；
 * 推导结果写入 SystemConfig（currentRound / roundOpen / roundOpenAt / roundCloseAt），
 * 学生端口令与登录页倒计时统一读取这几个字段，从而自动保持同步。
 */
@Service
public class RoundScheduleService {

    /** 支持的轮次数 */
    public static final int ROUND_COUNT = 3;

    @Autowired private RoundScheduleRepository repo;
    @Autowired private SystemConfigRepository sysRepo;

    /** 确保 1..3 三条记录存在，按轮次号升序返回 */
    public List<RoundSchedule> list() {
        List<RoundSchedule> all = new ArrayList<>(repo.findAll());
        boolean created = false;
        for (int i = 1; i <= ROUND_COUNT; i++) {
            final int n = i;
            if (all.stream().noneMatch(s -> s.getRoundNo() != null && s.getRoundNo() == n)) {
                all.add(new RoundSchedule(n));
                created = true;
            }
        }
        all.sort(Comparator.comparing(RoundSchedule::getRoundNo));
        if (created) repo.saveAll(all);
        return all;
    }

    /** 全量保存时间窗口，随后立即按当前时间同步一次状态 */
    public SystemConfig save(List<RoundSchedule> items, Boolean auto) {
        Map<Integer, RoundSchedule> map = new LinkedHashMap<>();
        for (RoundSchedule s : list()) map.put(s.getRoundNo(), s);
        if (items != null) {
            for (RoundSchedule in : items) {
                if (in == null || in.getRoundNo() == null) continue;
                RoundSchedule t = map.get(in.getRoundNo());
                if (t == null) continue;
                t.setOpenAt(in.getOpenAt());
                t.setCloseAt(in.getCloseAt());
            }
        }
        repo.saveAll(map.values());
        if (auto != null) {
            SystemConfig c = config();
            c.setAutoSchedule(auto);
            sysRepo.save(c);
        }
        return sync();
    }

    private SystemConfig config() {
        return sysRepo.findById(1L).orElseGet(() -> sysRepo.save(new SystemConfig()));
    }

    /** 定时巡检：到点自动开放 / 自动结束（每 30 秒） */
    @Scheduled(fixedRate = 30000)
    public void tick() { sync(); }

    /**
     * 依据当前时间推导「当前轮次 + 是否开放 + 倒计时窗口」。
     * - 仅自动模式开启、且至少配置了一个时间窗口时才生效；
     * - 状态无变化时不写库。
     */
    public synchronized SystemConfig sync() {
        SystemConfig c = config();
        if (!Boolean.TRUE.equals(c.getAutoSchedule())) return c;

        List<RoundSchedule> all = list();

        boolean anyConfigured = all.stream()
                .anyMatch(s -> s.getOpenAt() != null || s.getCloseAt() != null);
        // 尚未配置任何时间：保持现状，避免误关选课
        if (!anyConfigured) return c;

        LocalDateTime now = LocalDateTime.now();

        RoundSchedule active = null;      // 正在开放窗口内的轮次
        RoundSchedule upcoming = null;    // 下一个即将开放的轮次
        RoundSchedule lastPassed = null;  // 最后一个已结束的轮次

        for (RoundSchedule s : all) {
            LocalDateTime oa = s.getOpenAt(), ca = s.getCloseAt();
            if (oa == null && ca == null) continue;
            // 已到开始时间，且（未设结束时间 或 未到结束时间）→ 正在开放
            if (oa != null && !now.isBefore(oa) && (ca == null || now.isBefore(ca))) active = s;
            // 尚未到开始时间 → 候选「即将开放」
            if (oa != null && now.isBefore(oa) && upcoming == null) upcoming = s;
            // 已过结束时间 → 记录最后一个已结束轮次
            if (ca != null && !now.isBefore(ca)) lastPassed = s;
        }

        Integer newRound;
        boolean newOpen;
        LocalDateTime newOpenAt;
        LocalDateTime newCloseAt;

        if (active != null) {
            newRound = active.getRoundNo();
            newOpen = true;
            newOpenAt = active.getOpenAt();
            newCloseAt = active.getCloseAt();
        } else {
            newOpen = false;
            if (upcoming != null) {
                // 尚未开始：倒计时提示「距选课开放」，结束时间置空，避免误显示为「距本轮结束」
                newRound = upcoming.getRoundNo();
                newOpenAt = upcoming.getOpenAt();
                newCloseAt = null;
            } else {
                // 全部轮次已结束
                newRound = lastPassed != null ? lastPassed.getRoundNo() : c.getCurrentRound();
                newOpenAt = null;
                newCloseAt = null;
            }
        }

        boolean changed = !eq(c.getCurrentRound(), newRound)
                || !eq(c.getRoundOpen(), newOpen)
                || !eq(c.getRoundOpenAt(), newOpenAt)
                || !eq(c.getRoundCloseAt(), newCloseAt);
        if (changed) {
            c.setCurrentRound(newRound);
            c.setRoundOpen(newOpen);
            c.setRoundOpenAt(newOpenAt);
            c.setRoundCloseAt(newCloseAt);
            c = sysRepo.save(c);
        }
        return c;
    }

    /** 前端状态汇总：阶段 + 倒计时目标时间 */
    public Map<String, Object> status() {
        SystemConfig c = sync();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autoSchedule", Boolean.TRUE.equals(c.getAutoSchedule()));
        m.put("currentRound", c.getCurrentRound());
        m.put("roundOpen", Boolean.TRUE.equals(c.getRoundOpen()));
        m.put("roundOpenAt", c.getRoundOpenAt());
        m.put("roundCloseAt", c.getRoundCloseAt());
        m.put("now", now);

        String stage = "closed";             // opening / open / closed
        LocalDateTime target = null;
        if (Boolean.TRUE.equals(c.getRoundOpen())) {
            // 进行中：有结束时间则以结束时间为倒计时目标，未设结束时间则不限时
            stage = "open";
            target = c.getRoundCloseAt();
        } else if (c.getRoundOpenAt() != null && c.getRoundOpenAt().isAfter(now)) {
            stage = "opening";
            target = c.getRoundOpenAt();
        }
        m.put("stage", stage);
        m.put("targetAt", target);
        return m;
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
