import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Global test setup
// 必须有合法 origin,否则 node 24 内置 fetch 拒绝相对 URL
// (api/client.ts 的 baseUrl() 会把 path 拼到这个值上)
window._server = 'http://localhost';

// Mock bootbox globally
window.bootbox = {
    alert: vi.fn(),
    confirm: vi.fn(),
    prompt: vi.fn(),
    dialog: vi.fn(),
};
