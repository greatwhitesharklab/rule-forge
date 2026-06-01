/**
 * Monkey-patch document.addEventListener so that 'DOMContentLoaded' listeners
 * fire immediately when the DOM is already loaded.
 *
 * This is needed because editor entry points use dynamic import() via bootstrap.tsx.
 * By the time the editor module loads, DOMContentLoaded has already fired, so
 * normal addEventListener('DOMContentLoaded', fn) would never trigger.
 */
const origAdd = document.addEventListener;

document.addEventListener = function (
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions
) {
    if (type === 'DOMContentLoaded' && document.readyState !== 'loading') {
        // DOM already ready — invoke listener synchronously
        if (typeof listener === 'function') {
            listener(new Event('DOMContentLoaded'));
        } else if (listener && typeof listener.handleEvent === 'function') {
            listener.handleEvent(new Event('DOMContentLoaded'));
        }
        return;
    }
    return origAdd.call(document, type, listener, options);
} as typeof document.addEventListener;
