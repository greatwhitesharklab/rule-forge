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
    formatDate,
    getParameter,
    buildProjectNameFromFile,
    handleResponseError,
    buildEditorUrl,
} from './Utils.js';

describe('Utils - buildEditorUrl', () => {
    // GIVEN/WHEN/THEN — 修复 B-0:dev 环境编辑器空白 bug
    it('GIVEN editorPath already containing a query string WHEN building url THEN it should append file with &', () => {
        const url = buildEditorUrl('/html/editor.html?type=ruleset', '/e2e-test/rs-demo.rs.xml');
        expect(url).toBe('/html/editor.html?type=ruleset&file=/e2e-test/rs-demo.rs.xml');
    });

    it('GIVEN editorPath without query string WHEN building url THEN it should start file param with ?', () => {
        const url = buildEditorUrl('/html/editor.html', '/p/f.xml');
        expect(url).toBe('/html/editor.html?file=/p/f.xml');
    });

    it('GIVEN any editorPath WHEN building url THEN it must NOT produce double "html/" path or double "?" (B-0 regression guard)', () => {
        const url = buildEditorUrl('/html/editor.html?type=ruleset', '/p/f.xml');
        expect(url).not.toContain('/html/html/');
        expect(url).not.toMatch(/\?[^/]*\?/);
    });

    it('GIVEN a resource package file WHEN building url THEN it should append the file verbatim', () => {
        const url = buildEditorUrl('/html/editor.html?type=package', 'myproj.rp');
        expect(url).toBe('/html/editor.html?type=package&file=myproj.rp');
    });

    it('GIVEN editorPath as a debug function (non-file node) WHEN building url THEN it should return empty string (safe degradation)', () => {
        expect(buildEditorUrl(() => {}, '/p/f.xml')).toBe('');
    });
});

describe('Utils - formatDate', () => {
    it('GIVEN a Date object and "yyyy-MM-dd" format WHEN formatDate is called THEN it should return formatted date string', () => {
        const date = new Date(2025, 5, 15, 10, 30, 45);
        const result = formatDate(date, 'yyyy-MM-dd');
        expect(result).toBe('2025-06-15');
    });

    it('GIVEN a Date object and "yyyy-MM-dd HH:mm:ss" format WHEN formatDate is called THEN it should include time', () => {
        const date = new Date(2025, 0, 3, 8, 5, 9);
        const result = formatDate(date, 'yyyy-MM-dd HH:mm:ss');
        expect(result).toBe('2025-01-03 08:05:09');
    });

    it('GIVEN a number timestamp WHEN formatDate is called THEN it should convert to Date and format', () => {
        const timestamp = new Date(2025, 11, 25, 14, 30, 0).getTime();
        const result = formatDate(timestamp, 'yyyy/MM/dd');
        expect(result).toBe('2025/12/25');
    });

    it('GIVEN a string input WHEN formatDate is called THEN it should return the string unchanged', () => {
        const result = formatDate('2025-06-15', 'yyyy-MM-dd');
        expect(result).toBe('2025-06-15');
    });

    it('GIVEN a Date object and "MM/dd" format WHEN formatDate is called THEN it should format month and day', () => {
        const date = new Date(2025, 5, 5, 0, 0, 0);
        const result = formatDate(date, 'MM/dd');
        expect(result).toBe('06/05');
    });

    it('GIVEN a Date with single-digit month/day WHEN formatted with single-char pattern THEN it should not pad', () => {
        const date = new Date(2025, 0, 5, 9, 8, 7);
        const result = formatDate(date, 'yyyy-M-d H:m:s');
        expect(result).toBe('2025-1-5 9:8:7');
    });
});

describe('Utils - getParameter', () => {
    const originalSearch = (window as any).location.search;

    afterEach(() => {
        Object.defineProperty(window, 'location', {
            value: { search: originalSearch },
            writable: true,
            configurable: true,
        });
    });

    it('GIVEN URL has the parameter WHEN getParameter is called THEN it should return the parameter value', () => {
        Object.defineProperty(window, 'location', {
            value: { search: '?foo=bar&baz=qux' },
            writable: true,
            configurable: true,
        });
        expect(getParameter('foo')).toBe('bar');
        expect(getParameter('baz')).toBe('qux');
    });

    it('GIVEN URL does not have the parameter WHEN getParameter is called THEN it should return null', () => {
        Object.defineProperty(window, 'location', {
            value: { search: '?foo=bar' },
            writable: true,
            configurable: true,
        });
        expect(getParameter('missing')).toBeNull();
    });

    it('GIVEN URL has empty search WHEN getParameter is called THEN it should return null', () => {
        Object.defineProperty(window, 'location', {
            value: { search: '' },
            writable: true,
            configurable: true,
        });
        expect(getParameter('foo')).toBeNull();
    });
});

describe('Utils - buildProjectNameFromFile', () => {
    it('GIVEN a file path starting with "/" WHEN buildProjectNameFromFile is called THEN it should extract the first segment', () => {
        expect(buildProjectNameFromFile('/projectA/rules/test.xml')).toBe('projectA');
    });

    it('GIVEN a file path with single segment WHEN buildProjectNameFromFile is called THEN it should return the segment (no further slash)', () => {
        // substring(0, -1) returns empty string when indexOf("/") returns -1
        expect(buildProjectNameFromFile('/projectA')).toBe('');
    });

    it('GIVEN a file path not starting with "/" WHEN buildProjectNameFromFile is called THEN it should return undefined', () => {
        expect(buildProjectNameFromFile('projectA/rules/test.xml')).toBeUndefined();
    });
});

describe('Utils - handleResponseError', () => {
    beforeEach(() => {
    });

    afterEach(() => {
    });

    it('GIVEN a 401 response WHEN handleResponseError is called THEN it should alert permission denied', () => {
        handleResponseError({ status: 401 } as any);
        expect(mocks.alert).toHaveBeenCalledWith('权限不足，不能进行此操作.');
    });

    it('GIVEN a response with text method and error body WHEN handleResponseError is called THEN it should alert the error text with prefix', async () => {
        const response = {
            status: 500,
            text: vi.fn().mockResolvedValue('Something went wrong'),
        };

        const result = handleResponseError(response as any, 'Error:');
        await result;

        expect(mocks.alert).toHaveBeenCalledWith(
            "<span style='color: red'>Error:Something went wrong</span>"
        );
    });

    it('GIVEN a response with text method and empty body WHEN handleResponseError is called THEN it should alert with prefix only', async () => {
        const response = {
            status: 500,
            text: vi.fn().mockResolvedValue(''),
        };

        const result = handleResponseError(response as any, 'Error:');
        await result;

        expect(mocks.alert).toHaveBeenCalledWith(
            "<span style='color: red'>Error:</span>"
        );
    });

    it('GIVEN a response with no text method WHEN handleResponseError is called THEN it should alert generic error', () => {
        handleResponseError({ status: 500 } as any);
        expect(mocks.alert).toHaveBeenCalledWith(
            "<span style='color: red'>服务端出错</span>"
        );
    });

    it('GIVEN a response with no text method and custom prefix WHEN handleResponseError is called THEN it should alert with prefix', () => {
        handleResponseError({ status: 503 } as any, 'Custom prefix');
        expect(mocks.alert).toHaveBeenCalledWith(
            "<span style='color: red'>Custom prefix</span>"
        );
    });

    it('GIVEN a response with text method and no prefix WHEN handleResponseError is called THEN it should use default prefix', async () => {
        const response = {
            status: 500,
            text: vi.fn().mockResolvedValue('detail'),
        };

        const result = handleResponseError(response as any);
        await result;

        expect(mocks.alert).toHaveBeenCalledWith(
            "<span style='color: red'>服务端错误：detail</span>"
        );
    });
});
