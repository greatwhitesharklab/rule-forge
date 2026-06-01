import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
    formPost,
    jsonPost,
    jsonPut,
    httpGet,
    httpDelete,
    save,
    saveNewVersion,
} from './client.js';

// Mock window._server and window.bootbox
beforeEach(function () {
    (window as any)._server = 'http://localhost:8081';
    window.bootbox = {
        alert: vi.fn(),
        confirm: vi.fn(),
        prompt: vi.fn(),
        dialog: vi.fn(),
        setDefaults: vi.fn(),
    } as any;
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
        expect(window.bootbox.alert).toHaveBeenCalled();
    });

    it('should show permission alert on 401', async function () {
        mockFetchError(401);
        await expect(formPost('/test', {})).rejects.toBeDefined();
        expect(window.bootbox.alert).toHaveBeenCalledWith('权限不足，不能进行此操作.');
    });

    it('should not show alert when silent is true', async function () {
        mockFetchError(500);
        await expect(formPost('/test', {}, { silent: true })).rejects.toBeDefined();
        expect(window.bootbox.alert).not.toHaveBeenCalled();
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
        expect(window.bootbox.alert).toHaveBeenCalledWith('保存失败');
    });

    it('should not show alert when status false and silent', async function () {
        mockFetch({ status: false, message: '保存失败' });
        await expect(save('/save', {}, { silent: true })).rejects.toBeDefined();
        expect(window.bootbox.alert).not.toHaveBeenCalledWith('保存失败');
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
        expect(window.bootbox.alert).toHaveBeenCalledWith(
            '与最新版本无差异，无需生成新版本',
        );
    });
});
