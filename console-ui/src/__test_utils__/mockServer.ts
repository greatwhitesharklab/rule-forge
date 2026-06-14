import { vi } from 'vitest';

export function setupMockServer() {
    // V5.72: 旧版会设 window._server 让 client.ts apiBase 拼绝对 URL;V5.72 之后
    // apiBase 改纯 Vite env,这里不再需要 hack window._server。
    // 调用方应该用 vi.stubEnv('VITE_API_BASE', 'http://...') mock base。
    const mockResponses = new Map<any, any>();
    const fetchMock = vi.fn(async (url: string | URL | Request, options?: RequestInit) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        for (const [pattern, handler] of mockResponses.entries()) {
            if (typeof pattern === 'string' && urlStr.includes(pattern)) {
                return handler(options);
            }
            if (pattern instanceof RegExp && pattern.test(urlStr)) {
                return handler(options);
            }
        }
        return { ok: true, status: 200, json: async () => ({ status: true }), text: async () => '' };
    });
    (global as any).fetch = fetchMock;

    return {
        mockResponse(pattern: string | RegExp, data: any) {
            mockResponses.set(pattern, () => ({
                ok: true,
                status: 200,
                json: async () => data,
                text: async () => typeof data === 'string' ? data : JSON.stringify(data),
            }));
        },
        mockError(pattern: string | RegExp, status = 500) {
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
    // V5.72: 旧版删 window._server;现在 client.ts 不再读此变量,无须处理
    (global as any).fetch = undefined;
}
