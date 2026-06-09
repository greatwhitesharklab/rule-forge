package com.ruleforge.console.migration;

import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 5.10-B: REST 入口.
 *
 * POST /ruleforge/migration/run
 *   req: { projectNames: [...], dryRun: bool }
 *   resp: 200 + MigrationReport JSON
 *
 * admin 门控(走 permissionService.isAdmin(),与 RuleForgeRepositoryServiceImpl:216 同款)
 */
@RestController
@RequestMapping("/ruleforge/migration")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    private final PermissionService permissionService;
    private final MigrationService migrationService;

    public MigrationController(PermissionService permissionService,
                               MigrationService migrationService) {
        this.permissionService = permissionService;
        this.migrationService = migrationService;
    }

    @PostMapping("/run")
    public MigrationReport run(@RequestBody MigrationRequest request) {
        if (!permissionService.isAdmin()) {
            throw new NoPermissionException();
        }
        log.info("Migration invoked: projectNames={} dryRun={}",
                request.getProjectNames(), request.isDryRun());
        return migrationService.migrate(request);
    }
}
