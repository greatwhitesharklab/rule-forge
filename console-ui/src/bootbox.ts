/**
 * Custom bootbox dialog library (TypeScript).
 *
 * Provides alert, confirm, prompt, and generic dialog popups
 * using Bootstrap 3 modal markup. Side-effect: sets window.bootbox.
 */

interface DialogOptions {
    title?: string;
    message?: string;
    onEscape?: boolean;
    closeButton?: boolean;
    size?: 'large' | 'small' | '';
    buttons?: Record<string, ButtonConfig>;
    callback?: (result?: unknown) => void;
}

interface ButtonConfig {
    label?: string;
    className?: string;
    callback?: (el: HTMLElement) => boolean | void;
}

interface DialogHandle {
    modal(action: 'hide'): void;
}

let _backdrop: HTMLElement | null = null;
let _currentModal: HTMLElement | null = null;

function createBackdrop(): HTMLElement {
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop fade in';
    document.body.appendChild(backdrop);
    document.body.classList.add('modal-open');
    return backdrop;
}

function removeBackdrop(): void {
    if (_backdrop) {
        _backdrop.remove();
        _backdrop = null;
    }
    if (!_currentModal) {
        document.body.classList.remove('modal-open');
    }
}

function closeModal(modal: HTMLElement, callback?: (result?: unknown) => void, value?: unknown): void {
    modal.style.display = 'none';
    modal.remove();
    _currentModal = null;
    removeBackdrop();
    if (callback) callback(value);
}

function showDialog(options: DialogOptions): DialogHandle {
    const title = options.title || '';
    const message = options.message || '';
    const onEscape = options.onEscape !== false;
    const closeButton = options.closeButton !== false;
    const size = options.size || '';
    const buttons = options.buttons || {};
    const callback = options.callback;

    const modal = document.createElement('div');
    modal.className = 'modal fade in';
    modal.style.display = 'block';
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('tabindex', '-1');

    const sizeClass = size === 'large' ? ' modal-lg' : size === 'small' ? ' modal-sm' : '';
    const btnKeys = Object.keys(buttons);

    let html = '<div class="modal-dialog' + sizeClass + '">' +
        '<div class="modal-content">';

    if (title || closeButton) {
        html += '<div class="modal-header">';
        if (closeButton) {
            html += '<button type="button" class="bootbox-close-button close" aria-hidden="true">&times;</button>';
        }
        html += '<h4 class="modal-title">' + title + '</h4></div>';
    }

    html += '<div class="modal-body"><div class="bootbox-body">' + message + '</div></div>';

    if (btnKeys.length > 0 || callback) {
        html += '<div class="modal-footer">';
        if (btnKeys.length > 0) {
            for (let i = 0; i < btnKeys.length; i++) {
                const btn = buttons[btnKeys[i]];
                html += '<button type="button" class="btn ' + (btn.className || 'btn-default') +
                    '" data-bb-handler="' + btnKeys[i] + '">' + (btn.label || btnKeys[i]) + '</button>';
            }
        } else if (callback) {
            html += '<button type="button" class="btn btn-primary bootbox-accept">OK</button>';
        }
        html += '</div>';
    }

    html += '</div></div>';
    modal.innerHTML = html;
    document.body.appendChild(modal);
    _currentModal = modal;
    _backdrop = createBackdrop();

    // Close button
    const closeBtn = modal.querySelector('.bootbox-close-button');
    if (closeBtn) {
        closeBtn.addEventListener('click', () => {
            closeModal(modal, callback);
        });
    }

    // Named buttons
    const btnEls = modal.querySelectorAll('[data-bb-handler]');
    for (let i = 0; i < btnEls.length; i++) {
        const el = btnEls[i] as HTMLElement;
        const handler = el.getAttribute('data-bb-handler')!;
        const btnConfig = buttons[handler];
        el.addEventListener('click', () => {
            if (btnConfig && btnConfig.callback) {
                const result = btnConfig.callback(el);
                if (result === false) return;
            }
            closeModal(modal, callback);
        });
    }

    // Simple callback button
    const acceptBtn = modal.querySelector('.bootbox-accept');
    if (acceptBtn && !btnKeys.length) {
        acceptBtn.addEventListener('click', () => {
            closeModal(modal, callback);
        });
    }

    // Escape key
    if (onEscape) {
        const escHandler = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                document.removeEventListener('keydown', escHandler);
                closeModal(modal, callback);
            }
        };
        document.addEventListener('keydown', escHandler);
    }

    return {
        modal(action: 'hide') {
            if (action === 'hide') {
                closeModal(modal, callback);
            }
        }
    };
}

interface BootboxAlertOptions {
    title?: string;
    message: string;
    callback?: () => void;
}

interface BootboxConfirmOptions {
    title?: string;
    message: string;
    callback?: (result: boolean) => void;
}

interface BootboxPromptOptions {
    title?: string;
    message?: string;
    callback?: (result: string | null) => void;
}

function alert(msg: string | BootboxAlertOptions, cb?: () => void): DialogHandle {
    let title = '';
    let message = '';
    let callback: (() => void) | undefined;
    if (typeof msg === 'object') {
        title = msg.title || '';
        message = msg.message || '';
        callback = cb || msg.callback;
    } else {
        message = msg;
        callback = cb;
    }
    return showDialog({
        title,
        message,
        closeButton: false,
        buttons: {
            ok: {
                label: 'OK',
                className: 'btn-primary',
                callback: () => {
                    if (callback) callback();
                }
            }
        }
    });
}

function confirm(msg: string | BootboxConfirmOptions, cb?: (result: boolean) => void): DialogHandle {
    let title = '';
    let message = '';
    let callback: ((result: boolean) => void) | undefined;
    if (typeof msg === 'object') {
        title = msg.title || '';
        message = msg.message || '';
        callback = cb || msg.callback;
    } else {
        message = msg;
        callback = cb;
    }
    return showDialog({
        title,
        message,
        closeButton: false,
        buttons: {
            cancel: {
                label: 'Cancel',
                className: 'btn-default',
                callback: () => {
                    if (callback) callback(false);
                }
            },
            confirm: {
                label: 'OK',
                className: 'btn-primary',
                callback: () => {
                    if (callback) callback(true);
                }
            }
        }
    });
}

function prompt(msg: string | BootboxPromptOptions, cb?: (result: string | null) => void): DialogHandle {
    let title = '';
    let callback: ((result: string | null) => void) | undefined;
    if (typeof msg === 'object') {
        title = msg.title || '';
        callback = cb || msg.callback;
    } else {
        callback = cb;
    }
    return showDialog({
        title,
        message: '<form class="bootbox-form">' +
            '<input class="bootbox-input bootbox-input-text form-control" autocomplete="off" type="text" />' +
            '</form>',
        closeButton: false,
        buttons: {
            cancel: {
                label: 'Cancel',
                className: 'btn-default',
                callback: () => {
                    if (callback) callback(null);
                }
            },
            confirm: {
                label: 'OK',
                className: 'btn-primary',
                callback: () => {
                    const input = document.querySelector('.bootbox-input') as HTMLInputElement | null;
                    if (callback) callback(input ? input.value : null);
                }
            }
        }
    });
}

// Export for direct import usage
export { alert, confirm, prompt, showDialog };
export type { DialogHandle, DialogOptions, ButtonConfig };

// Side-effect: set window.bootbox for backward compatibility
window.bootbox = {
    alert,
    confirm,
    prompt,
    dialog: showDialog,
    setDefaults: () => {}
};
