package com.ruleforge.rete.perf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.91 — {@link FactIds} 契约 BDD。
 *
 * <p>锁 V5.91 新 perf test infra helper 的契约:
 * <ul>
 *   <li>{@code next(prefix)} 格式化 {@code "{prefix}-{counter}"},counter 全局原子递增</li>
 *   <li>{@code next()} 默认 prefix {@code "f"}</li>
 *   <li>连续 1000 次调用无重复(对应 bench 1000-fact 数据量)</li>
 *   <li>多线程并发安全(100 thread × 1000 call 应产出 100k unique)</li>
 *   <li>{@code reset()} 计数器归零</li>
 * </ul>
 *
 * <p>前序 V5.87 JFR 30s HotPathBenchTest 抓出
 * {@code UUID.randomUUID().toString()} 占 28% hot path(String.hashCode 313 +
 * SecureRandom 链 230+ = 543 leaf sample)。本 helper 用
 * {@link java.util.concurrent.atomic.AtomicLong} 计数器替代,zero-allocation +
 * zero-random,per-fact UUID cost 全部消除。详见
 * [[v591-test-factids-atomiclong]]。
 *
 * @since 5.91
 */
@DisplayName("V5.91 — FactIds (UUID → AtomicLong fact-name helper)")
class FactIdsTest {

    @AfterEach
    void resetCounter() {
        FactIds.reset();
    }

    @Nested
    @DisplayName("next(prefix) 格式化")
    class NextWithPrefix {

        // Given FactIds 干净状态
        // When 调 next("p") 然后 next("a")
        // Then 返回 "p-1" / "a-2",counter 原子递增
        @Test
        @DisplayName("next(\"p\") 返 \"p-1\",next(\"a\") 返 \"a-2\"")
        void nextWithPrefix_formatsPrefixCounter() {
            assertThat(FactIds.next("p")).isEqualTo("p-1");
            assertThat(FactIds.next("a")).isEqualTo("a-2");
            assertThat(FactIds.next("p")).isEqualTo("p-3");
        }
    }

    @Nested
    @DisplayName("next() 默认 prefix")
    class NextDefault {

        // Given FactIds 干净状态
        // When 调 next()
        // Then 返 "f-1"(默认 prefix "f")
        @Test
        @DisplayName("next() 默认 prefix 是 \"f\"")
        void nextDefaultPrefixIsF() {
            assertThat(FactIds.next()).isEqualTo("f-1");
            assertThat(FactIds.next()).isEqualTo("f-2");
        }
    }

    @Nested
    @DisplayName("唯一性")
    class Uniqueness {

        // Given FactIds
        // When 连续 1000 次调 next("u")
        // Then 1000 个返回值都 unique
        @Test
        @DisplayName("1000 次 next() 调用无重复")
        void nextReturnsUniqueStrings() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(FactIds.next("u"));
            }
            assertThat(ids).hasSize(1000);
        }

        // Given 100 thread
        // When 每个 thread 调 next() 1000 次
        // Then 总共 100k 调用,所有结果 unique(counter thread-safe)
        @Test
        @DisplayName("100 thread × 1000 call 产出 100k unique(AtomicLong thread-safe)")
        void nextIsThreadSafe() throws InterruptedException {
            int threadCount = 100;
            int perThread = 1000;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            ConcurrentLinkedQueue<String> all = new ConcurrentLinkedQueue<>();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            all.add(FactIds.next("t"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS))
                .as("所有 thread 在 10s 内完成")
                .isTrue();
            pool.shutdown();

            assertThat(all).hasSize(threadCount * perThread);
            assertThat(new HashSet<>(all)).hasSize(threadCount * perThread);
        }
    }

    @Nested
    @DisplayName("reset()")
    class Reset {

        // Given FactIds 已用过(n=5)
        // When reset() 然后 next("r")
        // Then 返 "r-1"(counter 归零)
        @Test
        @DisplayName("reset() 后 counter 归零")
        void resetClearsCounter() {
            FactIds.next("x");
            FactIds.next("x");
            FactIds.next("x");
            assertThat(FactIds.next("x")).isEqualTo("x-4");

            FactIds.reset();

            assertThat(FactIds.next("r")).isEqualTo("r-1");
        }
    }
}
