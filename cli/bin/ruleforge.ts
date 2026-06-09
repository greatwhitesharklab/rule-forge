#!/usr/bin/env -S npx tsx

import { Command } from 'commander';
import fs from 'fs';
import { loadConfig, saveConfig, apiGet, apiPost, parseDate, output, outputHealthTable } from '../lib/utils.js';

const program = new Command();
program
    .name('ruleforge')
    .description('RuleForge CLI - Agent-friendly command line interface')
    .version('1.0.0');

// === config ===
program.command('config')
    .description('Show or set configuration')
    .option('--server <url>', 'Set server URL')
    .action((opts) => {
        if (opts.server) {
            const config = loadConfig();
            config.server = opts.server;
            saveConfig(config);
            console.log(`Server set to: ${opts.server}`);
        } else {
            console.log(JSON.stringify(loadConfig(), null, 2));
        }
    });

// === analysis ===
const analysis = program.command('analysis').description('Decision log analysis');

analysis.command('flow-trend')
    .description('Decision flow time series trend')
    .option('--start <date>', 'Start time (ISO date or relative like 24h, 7d)', '24h')
    .option('--end <date>', 'End time (ISO date)', undefined)
    .option('--package <pkg>', 'Rule package path filter')
    .option('--granularity <g>', 'Time granularity: hourly or daily', 'hourly')
    .option('--format <fmt>', 'Output format: json or table', 'json')
    .action(async (opts) => {
        const params: Record<string, any> = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString(),
            granularity: opts.granularity
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/flow/timeseries', params);
        output(data, opts.format);
    });

analysis.command('reject-top')
    .description('Top reject codes distribution')
    .option('--start <date>', 'Start time', '24h')
    .option('--end <date>', 'End time')
    .option('--package <pkg>', 'Rule package filter')
    .option('--limit <n>', 'Top N', '20')
    .option('--format <fmt>', 'Output format', 'json')
    .action(async (opts) => {
        const params: Record<string, any> = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString(),
            limit: opts.limit
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/flow/reject-distribution', params);
        output(data, opts.format);
    });

analysis.command('package-summary')
    .description('Package/flow summary statistics')
    .option('--start <date>', 'Start time', '24h')
    .option('--end <date>', 'End time')
    .option('--format <fmt>', 'Output format', 'table')
    .action(async (opts) => {
        const params: Record<string, any> = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString()
        };
        const data = await apiGet('/analysis/flow/packages-summary', params);
        output(data, opts.format);
    });

analysis.command('rule-coverage')
    .description('Rule coverage analysis - hot/cold/dead rules')
    .option('--start <date>', 'Start time', '30d')
    .option('--end <date>', 'End time')
    .option('--package <pkg>', 'Rule package filter')
    .action(async (opts) => {
        const params: Record<string, any> = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString()
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/rule/coverage', params);
        output(data);
    });

analysis.command('rule-frequency')
    .description('Rule fire frequency ranking')
    .option('--start <date>', 'Start time', '24h')
    .option('--end <date>', 'End time')
    .option('--package <pkg>', 'Rule package filter')
    .option('--format <fmt>', 'Output format', 'table')
    .action(async (opts) => {
        const params: Record<string, any> = {
            startTime: parseDate(opts.start),
            endTime: opts.end ? new Date(opts.end).toISOString() : new Date().toISOString()
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/rule/fire-frequency', params);
        output(data, opts.format);
    });

analysis.command('anomaly')
    .description('Detect anomalies vs historical baseline')
    .option('--baseline-days <n>', 'Baseline period in days', '7')
    .option('--sigma <t>', 'Sigma threshold', '2.0')
    .option('--package <pkg>', 'Rule package filter')
    .action(async (opts) => {
        const params: Record<string, any> = {
            baselineDays: opts.baselineDays,
            sigmaThreshold: opts.sigma
        };
        if (opts.package) params.rulePackagePath = opts.package;
        const data = await apiGet('/analysis/anomaly/detect', params);
        output(data);
    });

analysis.command('packages')
    .description('List all rule package paths')
    .action(async () => {
        const data = await apiGet('/analysis/packages');
        output(data);
    });

// === rule (V5.22 — AI Rule Authoring) ===
const rule = program.command('rule').description('Rule authoring — list types, fetch schema, draft rules (V5.22)');

rule.command('list-types')
    .description('List all rule types supported by V5.22 AI authoring (decision_table, ul, ...)')
    .option('--format <fmt>', 'Output format: json or table', 'json')
    .action(async (opts) => {
        const data = await apiGet('/rule-schema/types');
        output(data, opts.format);
    });

rule.command('get-schema')
    .description('Get full JSON schema for a rule type (structure, operators, example, tips)')
    .requiredOption('--type <type>', 'Rule type, e.g. decision_table, ul, decision_tree')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiGet(`/rule-schema/${encodeURIComponent(opts.type)}`);
        output(data, opts.format);
    });

// V5.22 — 草稿生命周期 CLI (LLM agent 用)
rule.command('draft')
    .description('Create an AI rule draft (LLM agent). Pass content via --content or --content-file')
    .requiredOption('--rule-type <type>', 'Rule type, e.g. decision_table, ul')
    .requiredOption('--project <name>', 'Project name')
    .option('--content <json>', 'Rule JSON content (inline string)')
    .option('--content-file <path>', 'Read rule JSON from file (recommended for big rules)')
    .option('--title <title>', 'Draft title')
    .option('--created-by <name>', 'Created by (defaults to LLM)', 'LLM')
    .option('--session-id <id>', 'Optional LLM session id for audit')
    .option('--message-id <id>', 'Optional LLM message id for audit')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const content = opts.contentFile
            ? fs.readFileSync(opts.contentFile, 'utf8')
            : opts.content;
        if (!content) {
            console.error('ERROR: --content or --content-file is required');
            process.exit(1);
        }
        const data = await apiPost('/agent/tools/draft_rule', {
            ruleType: opts.ruleType,
            project: opts.project,
            content,
            createdBy: opts.createdBy,
            title: opts.title,
            sessionId: opts.sessionId,
            messageId: opts.messageId,
        });
        output(data, opts.format);
    });

rule.command('list-drafts')
    .description('List AI rule drafts (filtered by project or status)')
    .option('--project <name>', 'Project name')
    .option('--status <status>', 'Status: DRAFT / PENDING_REVIEW / APPROVED / REJECTED / EXPIRED')
    .option('--limit <n>', 'Max results', '50')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const params: Record<string, any> = { limit: parseInt(opts.limit, 10) };
        if (opts.project) params.project = opts.project;
        if (opts.status) params.status = opts.status;
        const data = await apiPost('/agent/tools/list_drafts', params);
        output(data, opts.format);
    });

rule.command('get-draft')
    .description('Get full draft detail (incl. content)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/get_draft', { draftId: opts.draftId });
        output(data, opts.format);
    });

rule.command('submit')
    .description('Submit draft for review (DRAFT → PENDING_REVIEW)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .option('--submitted-by <name>', 'Submitter', 'LLM')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/submit_draft', {
            draftId: opts.draftId,
            submittedBy: opts.submittedBy,
        });
        output(data, opts.format);
    });

rule.command('approve')
    .description('Approve a pending draft (PENDING_REVIEW → APPROVED)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .requiredOption('--reviewer <name>', 'Reviewer name')
    .option('--comment <text>', 'Approval comment')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/approve_draft', {
            draftId: opts.draftId,
            reviewer: opts.reviewer,
            comment: opts.comment,
        });
        output(data, opts.format);
    });

rule.command('reject')
    .description('Reject a pending draft (PENDING_REVIEW → REJECTED)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .requiredOption('--reviewer <name>', 'Reviewer name')
    .requiredOption('--reason <text>', 'Rejection reason')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/reject_draft', {
            draftId: opts.draftId,
            reviewer: opts.reviewer,
            reason: opts.reason,
        });
        output(data, opts.format);
    });

rule.command('apply')
    .description('Apply approved draft to a target package (write new file version)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .requiredOption('--package-path <path>', 'Target package path')
    .option('--file-name <name>', 'Output file name (default: rule_<type>_<id>.json)')
    .option('--reviewer <name>', 'Reviewer (for one-shot approve+apply)', 'LLM')
    .option('--version-comment <text>', 'Version comment (goes to git commit message)')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/apply_draft', {
            draftId: opts.draftId,
            packagePath: opts.packagePath,
            fileName: opts.fileName,
            reviewer: opts.reviewer,
            versionComment: opts.versionComment,
        });
        output(data, opts.format);
    });

rule.command('test-gen')
    .description('Generate test case templates for a draft (one per row)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .option('--count <n>', 'Max test cases to generate', '5')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/generate_test_cases', {
            draftId: opts.draftId,
            count: parseInt(opts.count, 10),
        });
        output(data, opts.format);
    });

rule.command('test-run')
    .description('Run test cases against a draft (local mock execution)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .option('--test-cases <json>', 'Inline JSON array of test cases')
    .option('--test-cases-file <path>', 'Read test cases from file')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const testCasesJson = opts.testCasesFile
            ? fs.readFileSync(opts.testCasesFile, 'utf8')
            : opts.testCases || '[]';
        // 后端接受 array 或 string;这里直接传 array 更干净
        const testCases = JSON.parse(testCasesJson);
        const data = await apiPost('/agent/tools/run_test', {
            draftId: opts.draftId,
            testCases,
        });
        output(data, opts.format);
    });

// V5.22.1 — 持久化测试用例(LLM/BA 落库,可重复跑)
rule.command('list-tests')
    .description('List persisted test cases for a draft')
    .requiredOption('--draft-id <id>', 'Draft id')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/list_test_cases', { draftId: opts.draftId });
        output(data, opts.format);
    });

rule.command('add-test')
    .description('Add a persisted test case to a draft (BA manual or LLM auto)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .requiredOption('--name <name>', 'Test case name')
    .option('--description <text>', 'Description')
    .requiredOption('--inputs <json>', 'Inputs JSON object, e.g. {"customer.age":17}')
    .option('--expected-row-id <id>', 'Expected row id (the row this case should match)')
    .option('--created-by <name>', 'Created by', 'BA')
    .option('--source <src>', 'Source: MANUAL or LLM', 'MANUAL')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/add_test_case', {
            draftId: opts.draftId,
            name: opts.name,
            description: opts.description,
            inputs: opts.inputs,
            expectedRowId: opts.expectedRowId,
            createdBy: opts.createdBy,
            source: opts.source,
        });
        output(data, opts.format);
    });

rule.command('del-test')
    .description('Delete a persisted test case')
    .requiredOption('--test-case-id <id>', 'Test case id')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/delete_test_case', { testCaseId: opts.testCaseId });
        output(data, opts.format);
    });

rule.command('run-saved-tests')
    .description('Run all persisted test cases for a draft (compare matched vs expected row)')
    .requiredOption('--draft-id <id>', 'Draft id')
    .option('--format <fmt>', 'Output format: json', 'json')
    .action(async (opts) => {
        const data = await apiPost('/agent/tools/run_saved_tests', { draftId: opts.draftId });
        output(data, opts.format);
    });

// V5.22.2 — 规则健康仪表盘(BA 每天看一眼)
// V5.22.3 — 加 --format=table 走 pretty print
rule.command('health')
    .description('Rule health dashboard — dead/hot rules, stale drafts, anomalies, top reject reasons')
    .option('--project <name>', 'Project name (omit for all projects)')
    .option('--days <n>', 'Time window in days', '30')
    .option('--format <fmt>', 'Output format: json/table', 'json')
    .action(async (opts) => {
        const params: Record<string, any> = { days: parseInt(opts.days, 10) };
        if (opts.project) params.project = opts.project;
        const data = await apiPost('/agent/tools/get_rule_health', params);
        if (opts.format === 'table') {
            outputHealthTable(data);
        } else {
            output(data, opts.format);
        }
    });

// === export ===
const exp = program.command('export').description('Export rule content');

exp.command('projects')
    .description('List all projects')
    .action(async () => {
        const data = await apiGet('/export/projects');
        output(data);
    });

exp.command('packages')
    .description('List packages in a project')
    .requiredOption('--project <name>', 'Project name')
    .action(async (opts) => {
        const data = await apiGet(`/export/project/${encodeURIComponent(opts.project)}/packages`);
        output(data);
    });

exp.command('package')
    .description('Export full package content with all files')
    .requiredOption('--project <name>', 'Project name')
    .requiredOption('--package-id <id>', 'Package ID or name')
    .action(async (opts) => {
        const data = await apiGet(`/export/project/${encodeURIComponent(opts.project)}/package/${encodeURIComponent(opts.packageId)}`);
        output(data);
    });

exp.command('file')
    .description('Export a single file content')
    .requiredOption('--path <path>', 'File path')
    .option('--version <ver>', 'Version')
    .action(async (opts) => {
        const params: Record<string, any> = { path: opts.path };
        if (opts.version) params.version = opts.version;
        const data = await apiGet('/export/file', params);
        output(data);
    });

// === decision (query logs) ===
const decision = program.command('decision').description('Query decision logs');

decision.command('list')
    .description('List recent decision logs (placeholder - uses monitoring API)')
    .option('--limit <n>', 'Max results', '20')
    .action(async (_opts) => {
        // This would need a dedicated endpoint; for now show package summary
        console.log('Note: Use ruleforge analysis flow-trend for execution data');
    });

program.parse();
