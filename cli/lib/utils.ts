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
