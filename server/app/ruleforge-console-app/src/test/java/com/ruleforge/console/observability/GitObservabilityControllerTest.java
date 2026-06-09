package com.ruleforge.console.observability;

import com.ruleforge.console.entity.GitDualwriteFailureEntity;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.repository.data.GitDualwriteFailureRepository;
import com.ruleforge.console.service.PermissionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GitObservabilityController (5.10-C)")
class GitObservabilityControllerTest {

    @Mock private PermissionService permissionService;
    @Mock private GitDualwriteFailureRepository failureRepository;

    private GitObservabilityController controller;

    @BeforeEach
    void setUp() {
        controller = new GitObservabilityController(permissionService, failureRepository,
                new SimpleMeterRegistry());
    }

    // ==========================================================================
    // Scenario 1: admin gate — non-admin 抛 NoPermissionException
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 1: admin gate")
    class AdminGate {

        @Test
        @DisplayName("Given non-admin 用户调 /summary, When summary(), "
                + "Then 抛 NoPermissionException, repository 一次都不调")
        void summary_rejectsNonAdmin() {
            // Given
            when(permissionService.isAdmin()).thenReturn(false);

            // When + Then
            assertThatThrownBy(() -> controller.summary())
                    .isInstanceOf(NoPermissionException.class);
            verify(failureRepository, never()).countAll();
        }

        @Test
        @DisplayName("Given non-admin 用户调 /recent, When recent(), Then 抛 NoPermissionException")
        void recent_rejectsNonAdmin() {
            // Given
            when(permissionService.isAdmin()).thenReturn(false);

            // When + Then
            assertThatThrownBy(() -> controller.recent(50))
                    .isInstanceOf(NoPermissionException.class);
            verify(failureRepository, never()).findRecent(anyInt());
        }
    }

    // ==========================================================================
    // Scenario 2: happy path — summary 包含 total/last1h/last24h/counters
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 2: happy path")
    class HappyPath {

        @Test
        @DisplayName("Given admin + repository 有数据, When summary(), "
                + "Then 返 totalFailures/last1h/last24h/counters 字段,且值与 repository 返回一致")
        void summary_returnsAllFields() {
            // Given
            when(permissionService.isAdmin()).thenReturn(true);
            when(failureRepository.countAll()).thenReturn(123L);
            when(failureRepository.countSince(org.mockito.ArgumentMatchers.any(Date.class)))
                    .thenReturn(7L, 42L);   // last1h=7, last24h=42

            // When
            Map<String, Object> result = controller.summary();

            // Then
            assertThat(result).containsEntry("totalFailures", 123L);
            assertThat(result).containsEntry("last1h", 7L);
            assertThat(result).containsEntry("last24h", 42L);
            assertThat(result).containsKey("counters");
            verify(failureRepository, times(1)).countAll();
            verify(failureRepository, times(2)).countSince(org.mockito.ArgumentMatchers.any(Date.class));
        }

        @Test
        @DisplayName("Given admin + repository 有 3 条失败, When recent(50), "
                + "Then 返 3 条记录")
        void recent_returnsRecords() {
            // Given
            when(permissionService.isAdmin()).thenReturn(true);
            GitDualwriteFailureEntity a = new GitDualwriteFailureEntity();
            a.setId(1L); a.setFilePath("/p/a.xml"); a.setErrorType("GitOperationException");
            GitDualwriteFailureEntity b = new GitDualwriteFailureEntity();
            b.setId(2L); b.setFilePath("/p/b.xml"); b.setErrorType("IOException");
            GitDualwriteFailureEntity c = new GitDualwriteFailureEntity();
            c.setId(3L); c.setFilePath("/p/c.xml"); c.setErrorType("GitOperationException");
            when(failureRepository.findRecent(50)).thenReturn(Arrays.asList(a, b, c));

            // When
            List<GitDualwriteFailureEntity> out = controller.recent(50);

            // Then
            assertThat(out).hasSize(3);
            assertThat(out.get(0).getFilePath()).isEqualTo("/p/a.xml");
            verify(failureRepository, times(1)).findRecent(50);
        }

        @Test
        @DisplayName("Given admin + limit=10000, When recent(10000), "
                + "Then 限到 500(保护)")
        void recent_clampsLimit() {
            // Given
            when(permissionService.isAdmin()).thenReturn(true);
            when(failureRepository.findRecent(500)).thenReturn(java.util.Collections.emptyList());

            // When
            controller.recent(10_000);

            // Then
            verify(failureRepository, times(1)).findRecent(500);
        }
    }
}
