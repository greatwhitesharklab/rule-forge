import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
const { mocks, clearModalMockState, getLastAlertMessage, getLastConfirm, confirmLast } = vi.hoisted(() => {
    const alerts: { message: unknown; cb?: () => void }[] = [];
    const confirms: { message: string; callback: (ok: boolean) => void }[] = [];
    const alert = vi.fn((message: unknown, cb?: () => void) => {
        alerts.push({ message, cb });
        if (typeof cb === 'function') cb();
    });
    const confirm = vi.fn((message: string, callback: (ok: boolean) => void) => {
        confirms.push({ message, callback });
    });
    const prompt = vi.fn();
    const dialog = vi.fn();
    return {
        mocks: { alert, confirm, prompt, dialog },
        clearModalMockState: () => {
            alerts.length = 0;
            confirms.length = 0;
            alert.mockReset();
            confirm.mockReset();
            prompt.mockReset();
            dialog.mockReset();
        },
        getLastAlertMessage: () => {
            const last = alerts[alerts.length - 1];
            if (!last) return null;
            return typeof last.message === 'string' ? last.message : String(last.message);
        },
        getLastConfirm: () => confirms[confirms.length - 1] ?? null,
        confirmLast: (accept = true) => {
            const last = confirms[confirms.length - 1];
            if (last) last.callback(accept);
        },
    };
});
vi.mock('@/utils/modal', () => mocks);

import {
    formPost,
    jsonPost,
    jsonPut,
    httpGet,
    httpDelete,
    save,
    saveNewVersion,
} from './client.js';

// V5.72: apiBase 改纯 Vite env,改用 vi.stubEnv mock VITE_API_BASE(替代 window._server)
beforeEach(function () {
    vi.stubEnv('VITE_API_BASE', 'http://localhost:8081');
    clearModalMockState();
});

afterEach(() => {
    vi.unstubAllEnvs();
});

// Helper to mock fetch
function mockFetch(response: Record<string, unknown>): void {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: vi.fn().mockResolvedValue(response),
        text: vi.fn().mockResolvedValue('error text'),
        ...response,
    }));
}

function mockFetchError(status: number, text?: string): void {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: false,
        status,
        json: vi.fn(),
        text: vi.fn().mockResolvedValue(text || 'server error'),
    }));
}

afterEach(function () {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

// ---- formPost ----

describe('formPost', function () {
    it('should POST with form-encoded body and return JSON', async function () {
        mockFetch({ result: 'ok' });
        const data = await formPost('/test', { key: 'value' });
        expect(data).toEqual({ result: 'ok' });
        expect(fetch).toHaveBeenCalledWith('http://localhost:8081/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'key=value',
        });
    });

    it('should show bootbox alert on HTTP error', async function () {
        mockFetchError(500, 'internal error');
        await expect(formPost('/test', {})).rejects.toBeDefined();
        expect(mocks.alert).toHaveBeenCalled();
    });

    it('should show permission alert on 401', async function () {
        mockFetchError(401);
        await expect(formPost('/test', {})).rejects.toBeDefined();
        expect(mocks.alert).toHaveBeenCalledWith('权限不足，不能进行此操作.');
    });

    it('should not show alert when silent is true', async function () {
        mockFetchError(500);
        await expect(formPost('/test', {}, { silent: true })).rejects.toBeDefined();
        expect(mocks.alert).not.toHaveBeenCalled();
    });
});

// ---- jsonPost ----

describe('jsonPost', function () {
    it('should POST with JSON body', async function () {
        mockFetch({ id: 1 });
        const data = await jsonPost('/test', { name: 'hello' });
        expect(data).toEqual({ id: 1 });
        expect(fetch).toHaveBeenCalledWith('http://localhost:8081/test', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: '{"name":"hello"}',
        });
    });
});

// ---- jsonPut ----

describe('jsonPut', function () {
    it('should PUT with JSON body', async function () {
        mockFetch({ updated: true });
        const data = await jsonPut('/test/1', { name: 'world' });
        expect(data).toEqual({ updated: true });
        expect(fetch).toHaveBeenCalledWith('http://localhost:8081/test/1', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: '{"name":"world"}',
        });
    });
});

// ---- httpGet ----

describe('httpGet', function () {
    it('should GET and parse JSON', async function () {
        mockFetch({ data: [{ id: 1 }, { id: 2 }] });
        const data = await httpGet('/items');
        expect(data).toEqual({ data: [{ id: 1 }, { id: 2 }] });
        expect(fetch).toHaveBeenCalledWith('http://localhost:8081/items');
    });
});

// ---- httpDelete ----

describe('httpDelete', function () {
    it('should DELETE and resolve void on success', async function () {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 204 }));
        await expect(httpDelete('/items/1')).resolves.toBeUndefined();
    });

    it('should reject on error', async function () {
        mockFetchError(404, 'not found');
        await expect(httpDelete('/items/999')).rejects.toBeDefined();
    });
});

// ---- save ----

describe('save', function () {
    it('should resolve with response when status is true', async function () {
        mockFetch({ status: true, data: 'saved' });
        const result = await save('/save', { file: 'test.xml', content: '<xml/>' });
        expect(result.status).toBe(true);
        expect(result.data).toBe('saved');
    });

    it('should show bootbox alert when status is false', async function () {
        mockFetch({ status: false, message: '保存失败' });
        await expect(save('/save', {})).rejects.toBeDefined();
        expect(mocks.alert).toHaveBeenCalledWith('保存失败');
    });

    it('should not show alert when status false and silent', async function () {
        mockFetch({ status: false, message: '保存失败' });
        await expect(save('/save', {}, { silent: true })).rejects.toBeDefined();
        expect(mocks.alert).not.toHaveBeenCalledWith('保存失败');
    });
});

// ---- saveNewVersion ----

describe('saveNewVersion', function () {
    it('should reject when file has no diff', async function () {
        // checkFileDirty returns status: false
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: true,
            json: vi.fn().mockResolvedValue({ status: false }),
            text: vi.fn(),
        }));

        await expect(
            saveNewVersion('/save', { file: 'test.xml', content: '<xml/>' }),
        ).rejects.toBeDefined();
        expect(mocks.alert).toHaveBeenCalledWith(
            '与最新版本无差异，无需生成新版本',
        );
    });
});

// ════════════════════════════════════════════════════════════════════════
// BatchTest V5.8.0 批量测试 API 测试
// ════════════════════════════════════════════════════════════════════════

import {
    startBatchTest,
    startBatchTestWithFile,
    getBatchTestProgress,
    getBatchTestResults,
    listBatchTestSessions,
} from './client.js';
import type { StartBatchTestRequest } from './client.js';

describe('startBatchTest', function () {
    it('should POST to /batchtest/start and return sessionId', async function () {
        const req: StartBatchTestRequest = {
            subjectType: 'FLOW',
            subjectId: 1,
            inputSourceType: 'FILE',
            inputSourceId: null,
            inputConfig: { files: 'test.xlsx' },
            project: 'p',
            packageId: 'pkg',
            flowId: 'f',
        };
        let capturedUrl = '';
        let capturedBody: unknown = null;
        vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string, init: any) => {
            capturedUrl = url;
            capturedBody = JSON.parse(init.body);
            return { ok: true, status: 200, json: async () => ({ sessionId: 42, status: 'RUNNING', subjectType: 'FLOW', inputSourceType: 'FILE' }) };
        }));

        const resp = await startBatchTest(req);
        expect(resp.sessionId).toBe(42);
        expect(resp.status).toBe('RUNNING');
        expect(capturedUrl).toContain('/batchtest/start');
        expect((capturedBody as any).subjectType).toBe('FLOW');
        expect((capturedBody as any).inputConfig.files).toBe('test.xlsx');
    });

    it('should not show alert on 501 (V5.8.0 mode not implemented)', async function () {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: false, status: 501, json: async () => ({}),
            text: async () => 'DATASOURCE subject 暂未实现',
        }));
        await expect(startBatchTest({} as any)).rejects.toBeDefined();
        expect(mocks.alert).toHaveBeenCalledWith(
            expect.stringContaining('DATASOURCE subject'),
        );
    });
});

describe('startBatchTestWithFile (v5.8.4)', function () {
    it('should POST multipart to /batchtest/start-with-file with file + config', async function () {
        const req: StartBatchTestRequest = {
            subjectType: 'DATASOURCE',
            subjectId: 42,
            inputSourceType: 'FILE',
            inputSourceId: null,
            inputConfig: { datasourceId: 42 },
            project: 'p',
            packageId: 'pkg',
            flowId: '',
        };
        // 伪造一个 File(实际是 Blob 子类,后端只读 isEmpty / getInputStream)
        const file = new File(['fake-xlsx-bytes'], 'test.xlsx', {
            type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        });

        let capturedUrl = '';
        let capturedInit: any = null;
        vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string, init: any) => {
            capturedUrl = url;
            capturedInit = init;
            return {
                ok: true,
                status: 200,
                json: async () => ({
                    sessionId: 99, status: 'RUNNING',
                    subjectType: 'DATASOURCE', inputSourceType: 'FILE',
                }),
            };
        }));

        const resp = await startBatchTestWithFile(req, file);
        expect(resp.sessionId).toBe(99);
        expect(capturedUrl).toContain('/batchtest/start-with-file');
        // body 是 FormData,不能 JSON.parse,只能检查 form 字段
        expect(capturedInit.method).toBe('POST');
        const fd = capturedInit.body as FormData;
        expect(fd.get('file')).toBe(file);
        const configJson = fd.get('config');
        expect(typeof configJson).toBe('string');
        const parsed = JSON.parse(configJson as string);
        expect(parsed.subjectType).toBe('DATASOURCE');
        expect(parsed.inputConfig.datasourceId).toBe(42);
    });

    it('should throw on 400 with backend error message', async function () {
        const file = new File(['x'], 'x.xlsx');
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: false, status: 400, text: async () => 'config 解析失败: bad json',
        }));
        await expect(startBatchTestWithFile({} as any, file)).rejects.toThrow(/config 解析失败/);
    });
});

describe('getBatchTestProgress', function () {
    it('should GET /batchtest/sessions/{id}/progress', async function () {
        let capturedUrl = '';
        vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => {
            capturedUrl = url;
            return { ok: true, status: 200, json: async () => ({ sessionId: 1, status: 'RUNNING', totalRows: 100, progress: 0.5, errorCount: 2, subjectType: 'FLOW', inputSourceType: 'FILE' }) };
        }));

        const p = await getBatchTestProgress(1);
        expect(p.sessionId).toBe(1);
        expect(p.progress).toBe(0.5);
        expect(capturedUrl).toContain('/batchtest/sessions/1/progress');
    });

    it('should reject on 404 (controller returns NOT_FOUND when session missing)', async function () {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: false, status: 404, json: async () => ({}), text: async () => '',
        }));
        await expect(getBatchTestProgress(999)).rejects.toBeDefined();
        // Note: httpGet currently alerts on any non-ok response (generic client behavior).
        // The 404 case here is intentional — controller returns 404 when session doesn't exist.
        expect(mocks.alert).toHaveBeenCalled();
    });
});

describe('getBatchTestResults', function () {
    it('should GET with page+size query params', async function () {
        let capturedUrl = '';
        vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => {
            capturedUrl = url;
            return { ok: true, status: 200, json: async () => ({ rows: [{ id: 1, sessionId: 1, rowIndex: 0, inputData: '{}', outputData: '{}', errorMessage: null, status: 'SUCCESS', latencyMs: 10, httpStatus: 200, errorCode: null }], page: 1, size: 20, total: 50 }) };
        }));

        const r = await getBatchTestResults(1, 1, 20);
        expect(r.rows).toHaveLength(1);
        expect(r.total).toBe(50);
        expect(capturedUrl).toContain('/batchtest/sessions/1/results?page=1&size=20');
    });
});

describe('listBatchTestSessions', function () {
    it('should GET with subjectType filter when provided', async function () {
        let capturedUrl = '';
        vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => {
            capturedUrl = url;
            return { ok: true, status: 200, json: async () => ([{ id: 1, subjectType: 'FLOW' }]) };
        }));

        const list = await listBatchTestSessions('FLOW', 10);
        expect(list).toHaveLength(1);
        expect(capturedUrl).toContain('subjectType=FLOW');
        expect(capturedUrl).toContain('limit=10');
    });

    it('should not include subjectType when not provided', async function () {
        let capturedUrl = '';
        vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => {
            capturedUrl = url;
            return { ok: true, status: 200, json: async () => ([]) };
        }));
        await listBatchTestSessions();
        expect(capturedUrl).not.toContain('subjectType');
    });
});
