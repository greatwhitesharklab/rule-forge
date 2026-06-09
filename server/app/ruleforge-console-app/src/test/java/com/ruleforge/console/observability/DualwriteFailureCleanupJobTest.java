package com.ruleforge.console.observability;

import com.ruleforge.console.repository.data.GitDualwriteFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 5.12 BDD: dualWrite 失败 audit log 定时清理.
 *
 * 验证 4 件事:
 *  1. 调用 deleteOlderThan(Date),Date 落在"现在 − 30 天"附近
 *  2. 仓库返 N 时,作业返 N
 *  3. 仓库抛异常时,作业吞掉,自己也不向上抛
 *  4. 每次调用只触发一次 deleteOlderThan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DualwriteFailureCleanupJob (5.12)")
class DualwriteFailureCleanupJobTest {

    @Mock
    private GitDualwriteFailureRepository dualwriteFailureRepository;

    private DualwriteFailureCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new DualwriteFailureCleanupJob(dualwriteFailureRepository);
    }

    @Nested
    @DisplayName("purgeOldFailures()")
    class PurgeOldFailures {

        @Test
        @DisplayName("Given a cleanup job, When purgeOldFailures() runs, "
                + "Then it calls deleteOlderThan with a Date ~30 days in the past")
        void givenJob_whenPurge_thenCallsDeleteOlderThanWith30DaysAgo() {
            when(dualwriteFailureRepository.deleteOlderThan(org.mockito.ArgumentMatchers.any(Date.class)))
                    .thenReturn(0);

            Instant before = Instant.now();
            job.purgeOldFailures();
            Instant after = Instant.now();

            ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
            verify(dualwriteFailureRepository).deleteOlderThan(captor.capture());
            Date cutoff = captor.getValue();

            // cutoff 应当落在 [before - 30d, after - 30d] 区间
            Instant cutoffInstant = cutoff.toInstant();
            assertThat(cutoffInstant).isBetween(
                    before.minus(30, ChronoUnit.DAYS).minusSeconds(1),
                    after.minus(30, ChronoUnit.DAYS).plusSeconds(1));
        }

        @Test
        @DisplayName("Given deleteOlderThan returns 7, When purgeOldFailures() runs, "
                + "Then purgeOldFailures() returns 7 (the count is observable)")
        void givenRepoReturnsN_whenPurge_thenNIsReturned() {
            when(dualwriteFailureRepository.deleteOlderThan(org.mockito.ArgumentMatchers.any(Date.class)))
                    .thenReturn(7);

            int purged = job.purgeOldFailures();

            assertThat(purged).isEqualTo(7);
        }

        @Test
        @DisplayName("Given deleteOlderThan throws RuntimeException, When purgeOldFailures() runs, "
                + "Then the exception is caught and the job does not propagate")
        void givenRepoThrows_whenPurge_thenExceptionIsSwallowed() {
            when(dualwriteFailureRepository.deleteOlderThan(org.mockito.ArgumentMatchers.any(Date.class)))
                    .thenThrow(new RuntimeException("DB down"));

            // 关键是:不抛
            assertThatCode(() -> job.purgeOldFailures())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Given multiple invocations, When invoked twice, "
                + "Then deleteOlderThan is called exactly once per invocation (no retry loop)")
        void givenMultipleInvocations_whenPurge_thenSingleDeleteCallPerInvocation() {
            when(dualwriteFailureRepository.deleteOlderThan(org.mockito.ArgumentMatchers.any(Date.class)))
                    .thenReturn(0);

            job.purgeOldFailures();
            job.purgeOldFailures();

            verify(dualwriteFailureRepository, org.mockito.Mockito.times(2))
                    .deleteOlderThan(org.mockito.ArgumentMatchers.any(Date.class));
            verifyNoMoreInteractions(dualwriteFailureRepository);
        }
    }
}
