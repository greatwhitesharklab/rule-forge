/**
 * Cell condition wrapper — renders a condition tree inside a Handsontable cell.
 *
 * Previously `ruleforge.CellCondition`.
 */

import { Join } from './Join.js';

// Context.js is still JS
interface ContextLike {
    getCanvas: () => HTMLElement;
    getPaper: () => PaperLike;
    setRootJoin: (join: Join) => void;
    getTotalChildrenCount: () => number;
}

interface PaperLike {
    path: (pathStr: string) => { attr: (a: Record<string, string>) => void; remove: () => void };
}

let conditionId = 0;

export class CellCondition {
    container: HTMLElement;
    join: Join;
    id: number;

    constructor(element: string | HTMLElement | ArrayLike<HTMLElement>) {
        if (typeof element === 'string') {
            const temp = document.createElement('div');
            temp.innerHTML = element;
            this.container = (temp.firstElementChild || temp) as HTMLElement;
        } else if (element && (element as HTMLElement).nodeType) {
            this.container = element as HTMLElement;
        } else if (element && (element as ArrayLike<HTMLElement>)[0]) {
            this.container = (element as ArrayLike<HTMLElement>)[0];
        } else {
            this.container = element as HTMLElement;
        }
        this.container.style.height = '40px';
        this.container.style.position = 'relative';
        const context = new (window as unknown as { ruleforge: { Context: new (c: HTMLElement) => ContextLike } }).ruleforge.Context(this.container) as ContextLike;
        this.join = new Join(context);
        this.join.init(null);
        this.join.initTopJoin(this.container);
        this.join.setType('and');
        this.id = conditionId++;
    }

    clean(): void {
        this.join.clean();
        window._setDirty?.();
    }

    getId(): number {
        return this.id;
    }

    renderTo(container: HTMLElement): void {
        container.append(this.container);
    }

    getDisplayContainer(): HTMLElement {
        let dis: HTMLElement | null = null;
        if (this.join) {
            dis = this.join.getDisplayContainer();
        }
        if (!dis) {
            dis = document.createElement('span');
            dis.style.cssText = 'color:gray';
            dis.textContent = '无';
        }
        return dis;
    }

    initData(data: Record<string, unknown>): void {
        if (this.join) {
            this.join.initData(data);
        }
    }

    toXml(): string {
        return this.join.toXml();
    }
}

// Backward-compatible global assignment
(window as unknown as Record<string, unknown>).ruleforge =
    (window as unknown as Record<string, Record<string, unknown>>).ruleforge || {};
((window as unknown as Record<string, Record<string, unknown>>).ruleforge).CellCondition = CellCondition;
