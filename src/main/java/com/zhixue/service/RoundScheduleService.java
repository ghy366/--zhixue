package com.zhixue.service;

import com.zhixue.entity.RoundSchedule;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.RoundScheduleRepository;
import com.zhixue.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
    @Autowired private CapacityService capacityService;
    /** @Lazy 打破循环：EnrollmentService 依赖本类，结算需反向调用它 */
    @Autowired @Lazy private EnrollmentService enrollmentService;

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
        SystemConfig c = config();
        if (auto != null) {
            c.setAutoSchedule(auto);
            c = sysRepo.save(c);
        }
        // 保存时间窗后无条件立即同步一次：无论自动/手动模式，
        // 侧边栏、登录页倒计时读取的 SystemConfig 都要拿到最新窗口
        return syncOnce(c);
    }

    private SystemConfig config() {
        return sysRepo.findById(1L).orElseGet(() -> sysRepo.save(new SystemConfig()));
    }

    /** 定时巡检：到点自动开放 / 自动结束（每 30 秒，首次延迟 10 秒，避开启动数据初始化） */
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    public void tick() { sync(); }

    /**
     * 依据当前时间推导「当前轮次 + 是否开放 + 倒计时窗口」。
     * - 仅自动模式开启、且至少配置了一个时间窗口时才生效；
     * - 状态无变化时不写库。
     */
    public synchronized SystemConfig sync() {
        SystemConfig c = config();
        // 自动模式关闭时不巡检：手动开放/关闭状态由用户控制，避免被时间窗覆盖
        if (!Boolean.TRUE.equals(c.getAutoSchedule())) return c;
        return syncOnce(c);
    }

    /** 按时间窗推导一次状态并写入 SystemConfig（无自动模式门槛，保存后立即调用用） */
    private synchronized SystemConfig syncOnce(SystemConfig c) {
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

        // 轮次门控：自动排班同样不能跳级，也不能在未开启新学期时开放第 1 轮
        int progress = c.getRoundProgress() == null ? 0 : Math.max(0, Math.min(3, c.getRoundProgress()));
        // 自动模式下某轮窗口已整体结束 → 与手动「关闭本轮」同语义：结算该轮并推进进度解锁下一轮。
        // lastPassed.roundNo > progress 保证每轮只结算/推进一次（幂等），避免每 30s 重复结算。
        if (lastPassed != null && lastPassed.getRoundNo() != null
                && lastPassed.getRoundNo() > progress && lastPassed.getRoundNo() <= ROUND_COUNT) {
            int passed = lastPassed.getRoundNo();
            try {
                if (passed <= 2) enrollmentService.settleRound(passed);   // 第 3 轮为补选，结算口径由手动处理
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(RoundScheduleService.class)
                        .warn("自动排班：第 {} 轮窗口结束后结算失败：{}", passed, ex.getMessage(), ex);
            }
            progress = passed;
            c.setRoundProgress(passed);
        }
        int unlocked = Math.min(3, progress + 1);
        if (newRound == null) newRound = unlocked;
        if (newRound > unlocked) { newRound = unlocked; newOpen = false; }
        if (newOpen && Integer.valueOf(1).equals(newRound)
                && !Boolean.TRUE.equals(c.getNewSemesterReady())) newOpen = false;

        boolean changed = !eq(c.getCurrentRound(), newRound)
                || !eq(c.getRoundOpen(), newOpen)
                || !eq(c.getRoundOpenAt(), newOpenAt)
                || !eq(c.getRoundCloseAt(), newCloseAt)
                || !eq(c.getRoundProgress(), progress);   // 窗口结束推进的进度也要落库，否则下次巡检重复结算
        if (changed) {
            // 自动排班不再清理报名数据：容量按学期口径释放，开启新学期由管理员显式操作
            c.setRoundProgress(progress);
            c.setCurrentRound(newRound);
            c.setRoundOpen(newOpen);
            c.setRoundOpenAt(newOpenAt);
            c.setRoundCloseAt(newCloseAt);
            c = sysRepo.save(c);
        }
        return c;
    }

    /**
     * 手动开放/关闭后，校正 SystemConfig 的时间窗，保证登录页/侧边栏倒计时能拿到正确目标时间。
     * 手动模式下 sync() 不巡检，时间窗只在这里维护。
     * 开放时：若现有窗口已整体过期（closeAt <= now，例如上轮测试遗留），
     * 过期窗口会压过 roundOpen=true 让登录页显示「已结束」——此时重置为「立即生效 · 不限时」；
     * 当前轮排期窗口尚未结束则沿用排期窗口。关闭时只补缺失字段，不回填已过期的排期。
     */
    public synchronized SystemConfig fillCurrentWindow() {
        SystemConfig c = config();
        Integer r = c.getCurrentRound() == null ? 1 : c.getCurrentRound();
        RoundSchedule hit = list().stream()
                .filter(s -> r.equals(s.getRoundNo()))
                .findFirst().orElse(null);
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;

        if (Boolean.TRUE.equals(c.getRoundOpen())) {
            boolean hitValid = hit != null && hit.getCloseAt() != null && hit.getCloseAt().isAfter(now);
            if (hitValid) {
                if (!hit.getOpenAt().equals(c.getRoundOpenAt())) {
                    c.setRoundOpenAt(hit.getOpenAt()); changed = true;
                }
                if (!hit.getCloseAt().equals(c.getRoundCloseAt())) {
                    c.setRoundCloseAt(hit.getCloseAt()); changed = true;
                }
            } else if (c.getRoundOpenAt() == null || c.getRoundCloseAt() != null) {
                // 无有效排期或旧窗口已过期：手动开放 = 立即生效、不限时
                c.setRoundOpenAt(now);
                c.setRoundCloseAt(null);
                changed = true;
            }
        } else {
            if (c.getRoundOpenAt() == null && hit != null && hit.getOpenAt() != null
                    && hit.getCloseAt() != null && hit.getCloseAt().isAfter(now)) {
                c.setRoundOpenAt(hit.getOpenAt()); changed = true;
            }
            if (c.getRoundCloseAt() == null && hit != null && hit.getCloseAt() != null
                    && hit.getCloseAt().isAfter(now)) {
                c.setRoundCloseAt(hit.getCloseAt()); changed = true;
            }
        }
        return changed ? sysRepo.save(c) : c;
    }

    /** 前端状态汇总：阶段 + 倒计时目标时间 */
    public Map<String, Object> status() {        SystemConfig c = sync();
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
