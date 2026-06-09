#!/usr/bin/env -S npx tsx

import { Command } from 'commander';
import { loadConfig, saveConfig, apiGet, parseDate, output } from '../lib/utils.js';

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
