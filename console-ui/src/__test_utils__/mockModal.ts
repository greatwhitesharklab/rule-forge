/**
 * Test utility for mocking the `utils/modal` module.
 *
 * The `vi.mock` factory in each test file MUST return the SAME `vi.fn()`
 * mocks that the test can also reference. We accomplish this with
 * `vi.hoisted`, which runs BEFORE imports and is shared between the
 * hoisted `vi.mock` factory and the test code.
 *
 * Usage (in each test file, at the top before any other imports):
 *
 *   import { clearModalMockState, getLastAlertMessage, confirmLast } from
 *       '../__test_utils__/mockModal';
 *
 *   const { mocks, ...helpers } = setupModalMock();
 *
 *   // `mocks` is { alert, confirm, prompt, dialog } — the same vi.fn() that
 *   // the production code sees, and the test can configure:
 *   mocks.prompt.mockImplementation((_msg, cb) => cb('hello'));
 *
 *   // Helpers (per-test-file state isolation):
 *   clearModalMockState();
 *   getLastAlertMessage();
 *   confirmLast(true);
 *
 * The function returns the mocks via a side-effecting `vi.hoisted` block.
 */
import { vi } from 'vitest';

export interface ModalMocks {
    alert: ReturnType<typeof vi.fn>;
    confirm: ReturnType<typeof vi.fn>;
    prompt: ReturnType<typeof vi.fn>;
    dialog: ReturnType<typeof vi.fn>;
    alerts: { message: unknown; cb?: () => void }[];
    confirms: { message: string; callback: (ok: boolean) => void }[];
}

/**
 * Side-effecting setup: registers a per-file `vi.hoisted` for the mock
 * functions and in-memory state. Returns a function the test can call to
 * retrieve the mocks.
 *
 * Actually, since `vi.hoisted` is per-call, we use a different pattern:
 * The test file should call this at the top, then reference the returned
 * `mocks` object directly.
 */
export function setupModalMock(modalModulePath: string): {
    mocks: ModalMocks;
    clearModalMockState: () => void;
    getLastAlertMessage: () => string | null;
    getConfirms: () => { message: string; callback: (ok: boolean) => void }[];
    getLastConfirm: () => { message: string; callback: (ok: boolean) => void } | null;
    confirmLast: (accept?: boolean) => void;
} {
    // Use `vi.hoisted` so this runs before the imports resolve and before
    // the `vi.mock` factory is invoked.
    const hoisted = vi.hoisted(() => {
        const alerts: { message: unknown; cb?: () => void }[] = [];
        const confirms: { message: string; callback: (ok: boolean) => void }[] = [];
        const alertFn = vi.fn((message: unknown, cb?: () => void) => {
            alerts.push({message, cb});
            if (typeof cb === 'function') cb();
        });
        const confirmFn = vi.fn((message: string, callback: (ok: boolean) => void) => {
            confirms.push({message, callback});
        });
        const promptFn = vi.fn();
        const dialogFn = vi.fn();
        return {
            mocks: {
                alert: alertFn,
                confirm: confirmFn,
                prompt: promptFn,
                dialog: dialogFn,
                alerts,
                confirms,
            },
        };
    });

    vi.doMock(modalModulePath, () => hoisted.mocks);

    const m = hoisted.mocks;

    function clearModalMockState(): void {
        m.alerts.length = 0;
        m.confirms.length = 0;
        m.alert.mockReset();
        m.confirm.mockReset();
        m.prompt.mockReset();
        m.dialog.mockReset();
    }

    function getLastAlertMessage(): string | null {
        const last = m.alerts[m.alerts.length - 1];
        if (!last) return null;
        return typeof last.message === 'string' ? last.message : String(last.message);
    }

    function getConfirms() {
        return m.confirms;
    }

    function getLastConfirm() {
        return m.confirms[m.confirms.length - 1] ?? null;
    }

    function confirmLast(accept = true): void {
        const last = m.confirms[m.confirms.length - 1];
        if (last) last.callback(accept);
    }

    return {
        mocks: m,
        clearModalMockState,
        getLastAlertMessage,
        getConfirms,
        getLastConfirm,
        confirmLast,
    };
}
