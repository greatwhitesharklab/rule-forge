import { vi } from 'vitest';

export function setupMockBootbox() {
    const alerts = [];
    const confirms = [];

    window.bootbox = {
        alert: vi.fn((msg, cb) => {
            alerts.push(typeof msg === 'string' ? msg : msg);
            if (typeof cb === 'function') cb();
        }),
        confirm: vi.fn((msg, cb) => {
            confirms.push({ message: msg, callback: cb });
        }),
        prompt: vi.fn(),
        dialog: vi.fn(),
    };

    return {
        getLastAlertMessage() {
            return alerts[alerts.length - 1] || null;
        },
        getAlerts() {
            return alerts;
        },
        getLastConfirm() {
            return confirms[confirms.length - 1] || null;
        },
        confirmLast(accept = true) {
            const last = confirms[confirms.length - 1];
            if (last) last.callback(accept);
        },
    };
}

export function teardownMockBootbox() {
    delete window.bootbox;
}
