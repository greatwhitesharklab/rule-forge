import { describe, test, expect, vi, beforeEach } from 'vitest';

/**
 * V5.44.4 — DRL 编辑器 round-trip API 客户端 BDD。
 *
 * <p>锁 4 件事:
 * <ol>
 *   <li>{@link loadDrlFile} 走 {@code POST /common/loadDrl},参数 file/version 编 URL 形式</li>
 *   <li>成功响应(200)直接返 payload,不解 imports/ruleNames(后端已 parse 好)</li>
 *   <li>404(文件不存在)走 silent 模式 throw,后端 /loadDrl 抛 404</li>
 *   <li>语法错 DRL 仍返 200 + 空 imports/ruleNames(后端 lenient 设计)</li>
 * </ol>
 *
 * <p>本测试**不**测后端逻辑(那是 console-app BDD 范围),只测 console-ui
 * 一侧的 client contract:URL、参数、解析、错误透传。
 */
describe('V5.44.4 — drlEditor loadDrlFile API', () => {
    beforeEach(() => {
        vi.restoreAllMocks();
    });

    // === BDD 1 — 走 /loadDrl 端点 ===
    test('loadDrlFile POSTs to /common/loadDrl with file param', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({
                path: '/proj/r.drl',
                version: '1.0',
                content: 'rule "R1" when eval(true) then end',
                imports: ['libs/variables.drl'],
                ruleNames: ['R1'],
            }),
        });
        vi.stubGlobal('fetch', fetchMock);
        // V5.72: 显式 stub VITE_API_BASE='' 让 apiBase() 走 '/api' hard default
        // (vitest.setup.js 把 process.env.VITE_API_BASE 设为 'http://localhost',不覆写会拿到 http://localhost)
        vi.stubEnv('VITE_API_BASE', '');

        const {loadDrlFile} = await import('./drlEditor');
        const result = await loadDrlFile('/proj/r.drl', '1.0');

        expect(fetchMock).toHaveBeenCalledOnce();
        const [url, init] = fetchMock.mock.calls[0];
        // apiBase() returns '/api' when VITE_API_BASE is empty → url is prefixed
        expect(url).toBe('/api/common/loadDrl');
        expect(init.method).toBe('POST');
        // body is URLSearchParams.toString() output — a string
        const body = init.body as string;
        expect(body).toContain('file=' + encodeURIComponent('/proj/r.drl'));
        expect(body).toContain('version=' + encodeURIComponent('1.0'));

        expect(result).toEqual({
            path: '/proj/r.drl',
            version: '1.0',
            content: 'rule "R1" when eval(true) then end',
            imports: ['libs/variables.drl'],
            ruleNames: ['R1'],
        });
    });

    // === BDD 2 — version 可选 ===
    test('loadDrlFile omits version param when not provided', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({
                path: '/proj/r.drl',
                content: '',
                imports: [],
                ruleNames: [],
            }),
        });
        vi.stubGlobal('fetch', fetchMock);
        // V5.72: apiBase 走 import.meta.env / process.env → '/api' default。
        // vitest.setup.js 把 process.env.VITE_API_BASE 设为 'http://localhost',所以这里
        // 用 vi.stubEnv 清空让它走 '/api' hard default(等价于 V5.72 之前设 window._server = '' 的效果)
        vi.stubEnv('VITE_API_BASE', '');

        const {loadDrlFile} = await import('./drlEditor');
        await loadDrlFile('/proj/r.drl');

        const body = fetchMock.mock.calls[0][1].body as string;
        expect(body).not.toContain('version=');
    });

    // === BDD 3 — 404 文件不存在 silent 模式 throw ===
    test('loadDrlFile throws on 404 (file not found)', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: false,
            status: 404,
            text: async () => 'file not found: /proj/missing.drl',
        });
        vi.stubGlobal('fetch', fetchMock);
        // V5.72: apiBase 走 import.meta.env / process.env → '/api' default。
        // vitest.setup.js 把 process.env.VITE_API_BASE 设为 'http://localhost',所以这里
        // 用 vi.stubEnv 清空让它走 '/api' hard default(等价于 V5.72 之前设 window._server = '' 的效果)
        vi.stubEnv('VITE_API_BASE', '');

        const {loadDrlFile} = await import('./drlEditor');
        await expect(loadDrlFile('/proj/missing.drl')).rejects.toBeDefined();
    });

    // === BDD 4 — 语法错 DRL 返 200 + 空 list ===
    test('loadDrlFile returns empty lists on syntax error (not throw)', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({
                path: '/proj/broken.drl',
                content: 'rule "R1" when broken then',
                imports: [],
                ruleNames: [],  // lenient parser 返回空,不抛
            }),
        });
        vi.stubGlobal('fetch', fetchMock);
        // V5.72: apiBase 走 import.meta.env / process.env → '/api' default。
        // vitest.setup.js 把 process.env.VITE_API_BASE 设为 'http://localhost',所以这里
        // 用 vi.stubEnv 清空让它走 '/api' hard default(等价于 V5.72 之前设 window._server = '' 的效果)
        vi.stubEnv('VITE_API_BASE', '');

        const {loadDrlFile} = await import('./drlEditor');
        const result = await loadDrlFile('/proj/broken.drl');

        expect(result.ruleNames).toEqual([]);
        expect(result.imports).toEqual([]);
        expect(result.content).toContain('broken');  // 原文透传
    });
});
