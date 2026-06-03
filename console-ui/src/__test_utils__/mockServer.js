import { vi } from 'vitest';

export function setupMockServer() {
    // 不要清成空串,否则 node 24 内置 fetch 拒绝相对 URL。
    // 复用 setup 文件已设的 'http://localhost'。
    window._server = window._server || 'http://localhost';
    const mockResponses = new Map();
    const fetchMock = vi.fn(async (url, options) => {
        for (const [pattern, handler] of mockResponses.entries()) {
            if (typeof pattern === 'string' && url.includes(pattern)) {
                return handler(options);
            }
            if (pattern instanceof RegExp && pattern.test(url)) {
                return handler(options);
            }
        }
        return { ok: true, status: 200, json: async () => ({ status: true }), text: async () => '' };
    });
    global.fetch = fetchMock;

    return {
        mockResponse(pattern, data) {
            mockResponses.set(pattern, () => ({
                ok: true,
                status: 200,
                json: async () => data,
                text: async () => typeof data === 'string' ? data : JSON.stringify(data),
            }));
        },
        mockError(pattern, status = 500) {
            mockResponses.set(pattern, () => ({
                ok: false,
                status,
                json: async () => ({ status: false, message: 'Server error' }),
                text: async () => 'Server error',
            }));
        },
        fetchMock,
    };
}

export function teardownMockServer() {
    delete window._server;
    global.fetch = undefined;
}
