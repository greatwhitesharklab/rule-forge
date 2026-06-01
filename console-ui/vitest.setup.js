import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Global test setup
window._server = '';

// Mock bootbox globally
window.bootbox = {
    alert: vi.fn(),
    confirm: vi.fn(),
    prompt: vi.fn(),
    dialog: vi.fn(),
};
