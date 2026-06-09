import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { loadConfig, saveConfig, getServer, apiGet, parseDate, output } from '../lib/utils.js';

/**
 * CLI 工具函数测试
 */
describe('CLI utils', () => {

    // ========== parseDate ==========

    describe('parseDate', () => {
        it('should return ISO string for null/undefined input', () => {
            const result = parseDate(null);
            expect(result).toMatch(/^\d{4}-\d{2}-\d{2}T/);
        });

        it('should parse relative hours (e.g. 1h, 6h, 24h)', () => {
            const before = Date.now();
            const result = parseDate('6h');
            const parsed = new Date(result).getTime();
            const expected = before - 6 * 3600000;
            expect(Math.abs(parsed - expected)).toBeLessThan(1000);
        });

        it('should parse relative days (e.g. 7d, 30d)', () => {
            const before = Date.now();
            const result = parseDate('7d');
            const parsed = new Date(result).getTime();
            const expected = before - 7 * 86400000;
            expect(Math.abs(parsed - expected)).toBeLessThan(1000);
        });

        it('should parse ISO date string', () => {
            const result = parseDate('2026-05-28T10:00:00.000Z');
            expect(result).toBe('2026-05-28T10:00:00.000Z');
        });

        it('should parse date-only string', () => {
            const result = parseDate('2026-05-28');
            expect(result).toMatch(/^2026-05-28/);
        });
    });

    // ========== output ==========

    describe('output', () => {
        let logSpy: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
        });

        afterEach(() => {
            logSpy.mockRestore();
        });

        it('should output JSON format by default', () => {
            output({ key: 'value' });
            expect(logSpy).toHaveBeenCalledWith(JSON.stringify({ key: 'value' }, null, 2));
        });

        it('should output explicit JSON format', () => {
            output([1, 2, 3], 'json');
            expect(logSpy).toHaveBeenCalledWith(JSON.stringify([1, 2, 3], null, 2));
        });

        it('should output table format for array of objects', () => {
            const data = [
                { name: 'Alice', age: 30 },
                { name: 'Bob', age: 25 }
            ];
            output(data, 'table');

            expect(logSpy).toHaveBeenCalledWith('name\tage');
            expect(logSpy).toHaveBeenCalledWith('---\t---');
            expect(logSpy).toHaveBeenCalledWith('Alice\t30');
            expect(logSpy).toHaveBeenCalledWith('Bob\t25');
        });

        it('should output (empty) for empty array in table format', () => {
            output([], 'table');
            expect(logSpy).toHaveBeenCalledWith('(empty)');
        });

        it('should fallback to JSON for non-array in table format', () => {
            output({ key: 'val' }, 'table');
            expect(logSpy).toHaveBeenCalledWith(JSON.stringify({ key: 'val' }, null, 2));
        });
    });

    // ========== config load/save ==========

    describe('loadConfig / saveConfig', () => {
        let tmpDir: string;

        beforeEach(() => {
            tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ruleforge-test-'));
        });

        afterEach(() => {
            fs.rmSync(tmpDir, { recursive: true, force: true });
        });

        it('should return default config when file does not exist', () => {
            const configPath = path.join(tmpDir, 'nonexistent.json');
            const config = loadConfig(configPath);
            expect(config).toEqual({ server: 'http://localhost:8180/ruleforgeV2' });
        });

        it('should save and load config', () => {
            const configPath = path.join(tmpDir, 'config.json');
            saveConfig({ server: 'http://custom:9999/api' }, configPath);
            const loaded = loadConfig(configPath);
            expect(loaded).toEqual({ server: 'http://custom:9999/api' });
        });

        it('should create directory if missing on save', () => {
            const configPath = path.join(tmpDir, 'deep', 'nested', 'config.json');
            saveConfig({ server: 'http://test:8080' }, configPath);
            expect(fs.existsSync(configPath)).toBe(true);
            const loaded = loadConfig(configPath);
            expect(loaded.server).toBe('http://test:8080');
        });
    });

    describe('getServer', () => {
        let tmpDir: string;

        beforeEach(() => {
            tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'ruleforge-test-'));
        });

        afterEach(() => {
            fs.rmSync(tmpDir, { recursive: true, force: true });
        });

        it('should return default server when no config', () => {
            const configPath = path.join(tmpDir, 'nonexistent.json');
            expect(getServer(configPath)).toBe('http://localhost:8180/ruleforgeV2');
        });

        it('should return configured server', () => {
            const configPath = path.join(tmpDir, 'config.json');
            saveConfig({ server: 'http://prod:8180/ruleforgeV2' }, configPath);
            expect(getServer(configPath)).toBe('http://prod:8180/ruleforgeV2');
        });

        it('should return default when server key is missing', () => {
            const configPath = path.join(tmpDir, 'config.json');
            saveConfig({}, configPath);
            expect(getServer(configPath)).toBe('http://localhost:8180/ruleforgeV2');
        });
    });

    // ========== apiGet ==========

    describe('apiGet', () => {
        it('should construct URL with params and fetch', async () => {
            const data = [{ id: 1 }];
            const mockFetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(data) });

            const result = await apiGet('/test', { key: 'val', num: 42 }, 'http://server/api', mockFetch as any);
            expect(result).toEqual(data);

            const calledUrl = mockFetch.mock.calls[0][0];
            expect(calledUrl).toContain('http://server/api/test');
            expect(calledUrl).toContain('key=val');
            expect(calledUrl).toContain('num=42');
        });

        it('should skip null/undefined params', async () => {
            const mockFetch = vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve({}) });

            await apiGet('/test', { a: '1', b: null, c: undefined }, 'http://server', mockFetch as any);
            const calledUrl = mockFetch.mock.calls[0][0];
            expect(calledUrl).toContain('a=1');
            expect(calledUrl).not.toContain('b=');
            expect(calledUrl).not.toContain('c=');
        });

        it('should throw on HTTP error', async () => {
            const mockFetch = vi.fn().mockResolvedValue({ ok: false, status: 500, statusText: 'Internal Server Error' });

            await expect(apiGet('/test', {}, 'http://server', mockFetch as any)).rejects.toThrow('HTTP 500');
        });
    });
});
