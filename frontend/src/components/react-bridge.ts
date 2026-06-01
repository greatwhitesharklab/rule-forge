import { createRoot, Root } from 'react-dom/client';
import { createElement, ComponentType } from 'react';

const rootCache = new WeakMap<Element | DocumentFragment, Root>();

export function renderReact<P extends Record<string, any>>(
    Component: ComponentType<P>,
    props: P,
    container: Element | DocumentFragment
): void {
    let root = rootCache.get(container);
    if (!root) {
        root = createRoot(container);
        rootCache.set(container, root);
    }
    root.render(createElement(Component, props));
}

export function unmountReact(container: Element | DocumentFragment): void {
    const root = rootCache.get(container);
    if (root) {
        root.unmount();
        rootCache.delete(container);
    }
}
