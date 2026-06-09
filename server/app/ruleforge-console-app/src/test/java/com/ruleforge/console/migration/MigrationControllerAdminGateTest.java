package com.ruleforge.console.migration;

import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.storage.GitStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 5.10-B: MigrationController admin gate 单测
 *
 * Feature: REST 入口必须 admin 才放行
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MigrationController admin gate (5.10-B)")
class MigrationControllerAdminGateTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private FileRepository fileRepository;
    @Mock private GitStorageService gitStorageService;
    @Mock private PermissionService permissionService;

    private MigrationController controller;

    @BeforeEach
    void setUp() {
        // 模拟非 admin
        when(permissionService.isAdmin()).thenReturn(false);

        controller = new MigrationController(
                permissionService,
                new MigrationService(
                        projectRepository, fileRepository, gitStorageService, null)
        );
    }

    @Test
    @DisplayName("Given caller 不是 admin, When POST /run, Then 抛 NoPermissionException")
    void nonAdminGetsNoPermission() {
        // Given — non-admin 已在 @BeforeEach 设好
        MigrationRequest req = new MigrationRequest(null, false);

        // When + Then
        assertThatThrownBy(() -> controller.run(req))
                .isInstanceOf(NoPermissionException.class);
    }
}
