package com.zhixue.config;

import com.zhixue.entity.RoundSchedule;
import com.zhixue.entity.SystemConfig;
import com.zhixue.repository.EnrollmentRepository;
import com.zhixue.service.EnrollmentService;
import com.zhixue.service.RoundScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动结算调度器。
 * <p>
 * 单独放在一个 Bean 里，是为了让 {@link EnrollmentService#settleRound(Integer)}
 * 通过 Spring 代理被调用，@Transactional 才会真正生效；
 * 如果直接在 EnrollmentService 内部自调用，事务和配置行锁都会失效。
 */
@Component
public class EnrollmentSettlementScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentSettlementScheduler.class);

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private RoundScheduleService roundScheduleService;
    @Autowired private EnrollmentRepository enrollmentRepo;

    /** 每 30 秒检查一次：仅自动排班模式、第 1/2 轮已结束且仍有 pending 报名时触发结算 */
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    public void autoSettleElapsedRounds() {
        SystemConfig config = roundScheduleService.sync();
        if (!Boolean.TRUE.equals(config.getAutoSchedule())) return;

        LocalDateTime now = LocalDateTime.now();
        for (RoundSchedule s : roundScheduleService.list()) {
            Integer round = s.getRoundNo();
            if (round == null || round < 1 || round > 2) continue; // 第三轮先到先得，无需结算
            LocalDateTime openAt = s.getOpenAt();
            LocalDateTime closeAt = s.getCloseAt();
            if (openAt == null || closeAt == null) continue;
            if (now.isBefore(openAt) || now.isBefore(closeAt)) continue;
            if (!enrollmentRepo.existsByRoundAndStatus(round, "pending")) continue;

            try {
                enrollmentService.settleRound(round);
            } catch (Exception e) {
                // 不吞异常：至少留下可排查的日志，下一轮巡检会再次尝试
                log.warn("第 {} 轮自动结算失败：{}", round, e.getMessage(), e);
            }
        }
    }
}
