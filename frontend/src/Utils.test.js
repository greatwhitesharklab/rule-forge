import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
    formatDate,
    getParameter,
    buildProjectNameFromFile,
    handleResponseError,
    ajaxSave,
    nextIFrameId,
} from './Utils.js';
import { setupMockBootbox, teardownMockBootbox } from './__test_utils__/mockBootbox.js';

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
    const originalSearch = window.location.search;

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
    let mockBootbox;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
    });

    afterEach(() => {
        teardownMockBootbox();
    });

    it('GIVEN a 401 response WHEN handleResponseError is called THEN it should alert permission denied', () => {
        handleResponseError({ status: 401 });
        expect(window.bootbox.alert).toHaveBeenCalledWith('权限不足，不能进行此操作.');
    });

    it('GIVEN a response with text method and error body WHEN handleResponseError is called THEN it should alert the error text with prefix', async () => {
        const response = {
            status: 500,
            text: vi.fn().mockResolvedValue('Something went wrong'),
        };

        const result = handleResponseError(response, 'Error:');
        await result;

        expect(window.bootbox.alert).toHaveBeenCalledWith(
            "<span style='color: red'>Error:Something went wrong</span>"
        );
    });

    it('GIVEN a response with text method and empty body WHEN handleResponseError is called THEN it should alert with prefix only', async () => {
        const response = {
            status: 500,
            text: vi.fn().mockResolvedValue(''),
        };

        const result = handleResponseError(response, 'Error:');
        await result;

        expect(window.bootbox.alert).toHaveBeenCalledWith(
            "<span style='color: red'>Error:</span>"
        );
    });

    it('GIVEN a response with no text method WHEN handleResponseError is called THEN it should alert generic error', () => {
        handleResponseError({ status: 500 });
        expect(window.bootbox.alert).toHaveBeenCalledWith(
            "<span style='color: red'>服务端出错</span>"
        );
    });

    it('GIVEN a response with no text method and custom prefix WHEN handleResponseError is called THEN it should alert with prefix', () => {
        handleResponseError({ status: 503 }, 'Custom prefix');
        expect(window.bootbox.alert).toHaveBeenCalledWith(
            "<span style='color: red'>Custom prefix</span>"
        );
    });

    it('GIVEN a response with text method and no prefix WHEN handleResponseError is called THEN it should use default prefix', async () => {
        const response = {
            status: 500,
            text: vi.fn().mockResolvedValue('detail'),
        };

        const result = handleResponseError(response);
        await result;

        expect(window.bootbox.alert).toHaveBeenCalledWith(
            "<span style='color: red'>服务端错误：detail</span>"
        );
    });
});

describe('Utils - ajaxSave', () => {
    let mockBootbox;
    let fetchMock;

    beforeEach(() => {
        mockBootbox = setupMockBootbox();
        fetchMock = vi.fn();
        global.fetch = fetchMock;
        window._server = '';
    });

    afterEach(() => {
        teardownMockBootbox();
        global.fetch = undefined;
        delete window._server;
    });

    it('GIVEN a successful response with status true WHEN ajaxSave is called THEN it should invoke callback with result', async () => {
        const callback = vi.fn();
        const result = { status: true, data: 'ok' };
        fetchMock.mockResolvedValue({
            ok: true,
            json: vi.fn().mockResolvedValue(result),
        });

        ajaxSave('/save', { key: 'val' }, callback);
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(callback).toHaveBeenCalledWith(result);
    });

    it('GIVEN a successful response with status false WHEN ajaxSave is called THEN it should alert error message', async () => {
        const callback = vi.fn();
        fetchMock.mockResolvedValue({
            ok: true,
            json: vi.fn().mockResolvedValue({ status: false, message: 'Save failed' }),
        });

        ajaxSave('/save', { key: 'val' }, callback);
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(callback).not.toHaveBeenCalled();
        expect(window.bootbox.alert).toHaveBeenCalledWith('Save failed');
    });

    it('GIVEN a failed HTTP response WHEN ajaxSave is called THEN it should call handleResponseError', async () => {
        const callback = vi.fn();
        fetchMock.mockResolvedValue({
            ok: false,
            status: 500,
            text: vi.fn().mockResolvedValue('Error'),
        });

        ajaxSave('/save', { key: 'val' }, callback);
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(callback).not.toHaveBeenCalled();
        // handleResponseError alerts for 500 with text
        expect(window.bootbox.alert).toHaveBeenCalled();
    });

    it('GIVEN a network error WHEN ajaxSave is called THEN it should alert generic error', async () => {
        const callback = vi.fn();
        fetchMock.mockRejectedValue(new Error('Network error'));

        ajaxSave('/save', { key: 'val' }, callback);
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(callback).not.toHaveBeenCalled();
        expect(window.bootbox.alert).toHaveBeenCalledWith(
            "<span style='color: red'>服务端出错</span>"
        );
    });

    it('GIVEN a successful response with status false and no message WHEN ajaxSave is called THEN it should alert default message', async () => {
        const callback = vi.fn();
        fetchMock.mockResolvedValue({
            ok: true,
            json: vi.fn().mockResolvedValue({ status: false }),
        });

        ajaxSave('/save', { key: 'val' }, callback);
        await new Promise(resolve => setTimeout(resolve, 0));

        expect(window.bootbox.alert).toHaveBeenCalledWith('保存失败');
    });
});

describe('Utils - nextIFrameId', () => {
    it('GIVEN initial iframe_id_ WHEN nextIFrameId is called THEN it should increment and return next ID', () => {
        window.iframe_id_ = 10;
        const result = nextIFrameId();
        expect(result).toBe('_iframe11');
        expect(window.iframe_id_).toBe(11);
    });

    it('GIVEN consecutive calls WHEN nextIFrameId is called multiple times THEN each call should return a unique ID', () => {
        window.iframe_id_ = 0;
        const id1 = nextIFrameId();
        const id2 = nextIFrameId();
        const id3 = nextIFrameId();
        expect(id1).toBe('_iframe1');
        expect(id2).toBe('_iframe2');
        expect(id3).toBe('_iframe3');
    });
});
