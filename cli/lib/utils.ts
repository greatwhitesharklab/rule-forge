import fs from 'fs';
import path from 'path';
import os from 'os';

export const CONFIG_PATH = path.join(os.homedir(), '.ruleforge', 'config.json');

export function loadConfig(configPath?: string) {
    const p = configPath || CONFIG_PATH;
    if (fs.existsSync(p)) {
        return JSON.parse(fs.readFileSync(p, 'utf8'));
    }
    return { server: 'http://localhost:8180/ruleforgeV2' };
}

export function saveConfig(config: any, configPath?: string) {
    const p = configPath || CONFIG_PATH;
    const dir = path.dirname(p);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(p, JSON.stringify(config, null, 2));
}

export function getServer(configPath?: string) {
    const config = loadConfig(configPath);
    return config.server || 'http://localhost:8180/ruleforgeV2';
}

export async function apiGet(
    pathStr: string,
    params?: Record<string, any>,
    serverUrl?: string,
    fetchFn?: typeof fetch
) {
    const url = new URL((serverUrl || getServer()) + pathStr);
    Object.entries(params || {}).forEach(([k, v]) => {
        if (v !== undefined && v !== null) url.searchParams.append(k, String(v));
    });
    const fn = fetchFn || fetch;
    const resp = await fn(url.toString());
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    return resp.json();
}

/**
 * POST JSON to a backend endpoint
 *
 * @param pathStr  e.g. "/rule-schema/types"
 * @param body     JSON-serializable body
 * @param serverUrl override server (default = config.server)
 * @param fetchFn  override fetch (for tests)
 */
export async function apiPost(
    pathStr: string,
    body?: unknown,
    serverUrl?: string,
    fetchFn?: typeof fetch
) {
    const url = (serverUrl || getServer()) + pathStr;
    const fn = fetchFn || fetch;
    const resp = await fn(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: body === undefined ? '' : JSON.stringify(body),
    });
    if (!resp.ok) {
        const text = await resp.text().catch(() => '');
        throw new Error(`HTTP ${resp.status}: ${resp.statusText}${text ? ' — ' + text : ''}`);
    }
    // Try JSON; if empty body, return null
    const text = await resp.text();
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch {
        return text;
    }
}

export function parseDate(str?: string | null) {
    if (!str) return new Date().toISOString();
    // Support relative: 1h, 6h, 24h, 7d, 30d
    const match = str.match(/^(\d+)(h|d)$/);
    if (match) {
        const ms = parseInt(match[1]) * (match[2] === 'h' ? 3600000 : 86400000);
        return new Date(Date.now() - ms).toISOString();
    }
    return new Date(str).toISOString();
}

export function output(data: any, format?: string) {
    format = format || 'json';
    if (format === 'json') {
        console.log(JSON.stringify(data, null, 2));
    } else if (format === 'table') {
        if (Array.isArray(data)) {
            if (data.length === 0) { console.log('(empty)'); return; }
            const keys = Object.keys(data[0]);
            // Header
            console.log(keys.join('\t'));
            console.log(keys.map(() => '---').join('\t'));
            data.forEach((row: any) => {
                console.log(keys.map(k => String(row[k] ?? '')).join('\t'));
            });
        } else {
            console.log(JSON.stringify(data, null, 2));
        }
    }
}

/**
 * V5.22.3 — 健康仪表盘专用 pretty print
 *
 * 健康响应是嵌套对象,普通 table 不友好(列名是字段路径)。
 * 拆成 5 个子表 + 头部,更直观。
 */
export function outputHealthTable(data: any) {
    if (!data || typeof data !== 'object') {
        console.log(JSON.stringify(data, null, 2));
        return;
    }
    const status = data.status || 'OK';
    const statusColor = status === 'DEGRADED' ? '❌' : status === 'PARTIAL' ? '⚠️ ' : '✅';
    console.log(`${statusColor} 状态: ${status}` +
        (data.failedSources && data.failedSources.length
            ? ` (失败: ${data.failedSources.join(', ')})` : ''));
    console.log(`项目: ${data.project || 'all'} · 时间窗口: ${data.days || 30} 天 · 更新于 ${(data.generatedAt || '').replace('T', ' ').slice(0, 16)}`);
    console.log('');

    // 1) 覆盖率
    const c = data.coverage || {};
    console.log('--- 覆盖率 ---');
    console.log(`  总规则: ${c.totalRules ?? '-'} · 活跃: ${c.activeRules ?? '-'} · 死规则: ${c.deadRules ?? '-'}`);
    console.log('');

    // 2) 热规则
    printList('热规则 Top', data.hotRules, ['ruleId', 'fireCount'], '次触发');

    // 3) 异常
    printList('最近异常', data.recentAnomalies, ['type', 'severity', 'message'], '');

    // 4) 拒绝原因
    printList('Top 拒绝原因', data.topRejectReasons, ['reason', 'count'], '次');

    // 5) 滞留草稿
    if (data.staleDrafts && data.staleDrafts.length) {
        console.log(`--- 滞留草稿 (${data.staleDrafts.length}) ---`);
        data.staleDrafts.forEach((d: any) => {
            console.log(`  ${d.draftId}  ${d.title || '(无标题)'}  [${d.status}]  ${d.project}  ${d.daysOld} 天前  by ${d.createdBy}`);
        });
        console.log('');
    } else {
        console.log('--- 滞留草稿 ---');
        console.log('  ✅ 无');
        console.log('');
    }
}

function printList(title: string, rows: any[] | undefined, keys: string[], suffix: string) {
    if (!rows || rows.length === 0) {
        console.log(`--- ${title} ---`);
        console.log('  (无)');
        console.log('');
        return;
    }
    console.log(`--- ${title} ---`);
    rows.forEach((r: any) => {
        const parts = keys.map(k => String(r[k] ?? '-'));
        const line = '  ' + parts.join('  ');
        console.log(suffix ? `${line}  ${suffix}` : line);
    });
    console.log('');
}
