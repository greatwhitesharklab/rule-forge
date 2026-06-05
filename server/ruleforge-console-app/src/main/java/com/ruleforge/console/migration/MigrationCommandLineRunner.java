package com.ruleforge.console.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 5.10-B: CLI 入口 (在 main jar 加 --ruleforge.migration.enabled=true 触发).
 *
 *   java -jar ruleforge-console-app.jar \
 *        --ruleforge.migration.enabled=true \
 *        --spring.main.web-application-type=none \
 *        [--project <name>...] [--dry-run]
 *
 * 跑完调 System.exit 让 fat jar 干净结束.
 * 报错时 exit(1).
 */
@Component
@Order(0)
@ConditionalOnProperty(prefix = "ruleforge.migration", name = "enabled", havingValue = "true")
public class MigrationCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationCommandLineRunner.class);
    private static final int EXIT_OK = 0;
    private static final int EXIT_FAIL = 1;

    private final MigrationService migrationService;

    public MigrationCommandLineRunner(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Override
    public void run(String... args) {
        ParsedArgs parsed;
        try {
            parsed = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Bad args: " + e.getMessage());
            printUsage();
            System.exit(EXIT_FAIL);
            return;
        }

        log.info("Migration CLI: dryRun={} projects={}", parsed.dryRun, parsed.projectNames);

        MigrationReport report = migrationService.migrate(
                new MigrationRequest(parsed.projectNames, parsed.dryRun));

        // stdout 印报告(运维 capture 友好)
        System.out.println(formatReport(report));

        log.info("Migration finished: totalProjects={} versionsMigrated={} versionsFailed={} projectsFailed={}",
                report.getTotalProjects(),
                report.getVersionsMigrated(),
                report.getVersionsFailed(),
                report.getProjectsFailed());

        int code = (report.getProjectsFailed() > 0 || report.getVersionsFailed() > 0 || !report.getGlobalErrors().isEmpty())
                ? EXIT_FAIL
                : EXIT_OK;
        System.exit(code);
    }

    /** Hand-formatted text report (避免 Jackson 依赖问题,运维 awk/grep 友好). */
    static String formatReport(MigrationReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Migration Report ===\n");
        sb.append("runId:                ").append(report.getRunId()).append('\n');
        sb.append("startedAt:            ").append(report.getStartedAt()).append('\n');
        sb.append("finishedAt:           ").append(report.getFinishedAt()).append('\n');
        sb.append("durationMs:           ").append(report.getDurationMs()).append('\n');
        sb.append("dryRun:               ").append(report.isDryRun()).append('\n');
        sb.append("requestedProjectNames:").append(report.getRequestedProjectNames()).append('\n');
        sb.append("--- Aggregate ---\n");
        sb.append("totalProjects:               ").append(report.getTotalProjects()).append('\n');
        sb.append("  projectsMigrated:          ").append(report.getProjectsMigrated()).append('\n');
        sb.append("  projectsSkippedClean:      ").append(report.getProjectsSkippedClean()).append('\n');
        sb.append("  projectsFailed:            ").append(report.getProjectsFailed()).append('\n');
        sb.append("totalVersionsSeen:           ").append(report.getTotalVersionsSeen()).append('\n');
        sb.append("  versionsMigrated:          ").append(report.getVersionsMigrated()).append('\n');
        sb.append("  versionsSkippedAlready:    ").append(report.getVersionsSkippedAlreadyMigrated()).append('\n');
        sb.append("  versionsSkippedNullContent:").append(report.getVersionsSkippedNullContent()).append('\n');
        sb.append("  versionsFailed:            ").append(report.getVersionsFailed()).append('\n');
        if (!report.getGlobalErrors().isEmpty()) {
            sb.append("globalErrors:\n");
            for (String e : report.getGlobalErrors()) {
                sb.append("  - ").append(e).append('\n');
            }
        }
        if (!report.getProjectResults().isEmpty()) {
            sb.append("--- Per-project ---\n");
            for (ProjectResult pr : report.getProjectResults()) {
                sb.append(String.format("[%s] %s (id=%s) migrated=%d skipped=%d failed=%d%n",
                        pr.getStatus(), pr.getProjectName(), pr.getProjectId(),
                        pr.getVersionsMigrated(), pr.getVersionsSkipped(), pr.getVersionsFailed()));
                for (VersionError ve : pr.getErrors()) {
                    sb.append(String.format("    ERR %s v%s [%s] %s%n",
                            ve.getFilePath(), ve.getVersionNum(), ve.getErrorType(), ve.getMessage()));
                }
            }
        }
        return sb.toString();
    }

    /** Visible for testing. */
    static ParsedArgs parseArgs(String[] args) {
        ParsedArgs out = new ParsedArgs();
        List<String> projectNames = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--project requires a value");
                    }
                    projectNames.add(args[++i]);
                }
                case "--dry-run" -> out.dryRun = true;
                case "-h", "--help" -> {
                    printUsage();
                    System.exit(EXIT_OK);
                }
                default -> {
                    // 透传给 Spring Boot 的 args,这里忽略 unknown
                }
            }
        }
        out.projectNames = projectNames;
        return out;
    }

    private static void printUsage() {
        String usage = String.join("\n",
                "RuleForge DB→Git migration tool (5.10-B)",
                "",
                "Usage: java -jar ruleforge-console-app.jar \\",
                "         --ruleforge.migration.enabled=true \\",
                "         --spring.main.web-application-type=none \\",
                "         [--project <name>...] [--dry-run]",
                "",
                "Options:",
                "  --project <name>   Migrate only the named project (repeatable). Omit = all projects.",
                "  --dry-run          Do not write to Git or update DB. Only produce a report.",
                "  -h, --help         Show this help."
        );
        System.out.println(usage);
    }

    /** Visible for testing. */
    static class ParsedArgs {
        List<String> projectNames = new ArrayList<>();
        boolean dryRun = false;
    }
}
