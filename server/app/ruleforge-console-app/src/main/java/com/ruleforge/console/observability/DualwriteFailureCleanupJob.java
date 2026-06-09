package com.ruleforge.console.observability;

import com.ruleforge.console.repository.data.GitDualwriteFailureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 5.12: dualWrite 失败 audit log 定时清理.
 *
 * 每天凌晨 3 点跑一次,删掉早于 30 天的失败行,防止表无界增长.
 * 异常吞掉 + 日志告警:清理任务挂了不能影响业务.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DualwriteFailureCleanupJob {

    /** 保留天数. */
    private static final int RETENTION_DAYS = 30;

    private final GitDualwriteFailureRepository dualwriteFailureRepository;

    /**
     * @Scheduled 入口(无返回值,Spring 不消费).
     * 仅做 delegate,真正的可测逻辑在 {@link #purgeOldFailures()}.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledPurge() {
        purgeOldFailures();
    }

    /**
     * 实际清理逻辑.返回删除条数,异常被吞 + 日志告警,绝不向上抛.
     * 可被单测直接 new + 调用,绕开 Spring AOP 代理.
     */
    public int purgeOldFailures() {
        Date cutoff = Date.from(Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS));
        try {
            int n = dualwriteFailureRepository.deleteOlderThan(cutoff);
            log.info("Purged {} old dualwrite failure rows (cutoff={})", n, cutoff);
            return n;
        } catch (Exception e) {
            log.error("Failed to purge old dualwrite failures (cutoff={})", cutoff, e);
            return 0;
        }
    }
}
