/**
 * Connection between Join and Condition/Join nodes in the RETE tree.
 *
 * Previously `ruleforge.Connection`.
 */

import { Condition } from './Condition.js';
import { Join } from './Join.js';

// Context.js is still JS
interface ContextLike {
    getCanvas: () => HTMLElement;
    getPaper: () => PaperLike;
    setRootJoin: (join: Join) => void;
    getTotalChildrenCount: () => number;
}

interface PaperLike {
    path: (pathStr: string) => PathElement;
}

interface PathElement {
    attr: (attrsOrKey: Record<string, string> | [string, string] | string, value?: string) => void;
    remove: () => void;
}

export class Connection {
    private isJoin: boolean;
    private context: ContextLike;
    private parentJoin: Join;
    private path!: PathElement;
    private startX: number = 0;
    private startY: number = 0;
    private endX: number = 0;
    private endY: number = 0;
    join: Join | null = null;
    condition: Condition | null = null;
    private conditionContainer: HTMLElement | null = null;

    constructor(context: ContextLike, isJoin: boolean, parentJoin: Join) {
        this.isJoin = isJoin;
        this.context = context;
        this.parentJoin = parentJoin;
    }

    drawPath(startX: number, startY: number, endX: number, endY: number): void {
        this.startX = startX;
        this.endX = endX;
        if (this.isJoin) {
            this.startY = startY - 3;
            this.endY = endY + 2;
        } else {
            this.startY = startY - 3;
            this.endY = endY - 3;
        }
        this.path = this.context.getPaper().path(this.buildPathInfo());
        this.path.attr({ 'stroke': '#777' });
        if (this.isJoin) {
            this.initJoin();
        } else {
            this.initCondition();
        }
    }

    toXml(): string {
        if (this.isJoin) {
            return this.join!.toXml();
        } else {
            return this.condition!.toXml();
        }
    }

    private initJoin(): void {
        this.join = new Join(this.context);
        this.join.init(this);
        const joinContainer = this.join.getContainer();
        const left = (this.endX + 10) + 'px';
        const top = this.endY + 'px';
        joinContainer.style.position = 'absolute';
        joinContainer.style.left = left;
        joinContainer.style.top = top;
        this.context.getCanvas().append(joinContainer);
    }

    getDisplayContainer(): HTMLElement {
        if (this.join) {
            return this.join.getDisplayContainer()!;
        } else {
            return this.condition!.getDisplayContainer();
        }
    }

    remove(): void {
        this.path.remove();
        if (this.join) {
            this.join.getContainer().remove();
        } else {
            this.conditionContainer!.remove();
        }
        window._setDirty?.();
    }

    private initCondition(): void {
        this.conditionContainer = document.createElement('div');
        const left = (this.endX + 10) + 'px';
        const top = this.endY + 'px';
        this.conditionContainer.style.position = 'absolute';
        this.conditionContainer.style.left = left;
        this.conditionContainer.style.top = top;
        this.condition = new Condition(this.conditionContainer);
        const del = document.createElement('i');
        del.className = 'glyphicon glyphicon-trash';
        del.style.cssText = 'color: #019dff;cursor: pointer;font-size: 9pt;padding-left:5px';
        const self = this;
        del.addEventListener('click', function () {
            self.parentJoin.removeConnection(self);
        });
        this.conditionContainer.appendChild(del);
        this.context.getCanvas().append(this.conditionContainer);
    }

    update(add: boolean): void {
        const pathInfo = this.buildPathInfo();
        this.path.attr('path', pathInfo);
        const top = this.endY + 'px';
        if (this.conditionContainer) {
            this.conditionContainer.style.top = top;
        } else {
            this.join!.getContainer().style.top = top;
        }
        if (this.join) {
            this.join.resetItemPosition(0, add);
        }
    }

    getParentJoin(): Join {
        return this.parentJoin;
    }

    getCondition(): Condition | null {
        return this.condition;
    }

    getJoin(): Join | null {
        return this.join;
    }

    getStartX(): number {
        return this.startX;
    }

    getStartY(): number {
        return this.startY;
    }

    getEndX(): number {
        return this.endX;
    }

    getEndY(): number {
        return this.endY;
    }

    setStartX(startX: number): void {
        this.startX = startX;
    }

    setStartY(startY: number): void {
        this.startY = startY;
    }

    setEndX(endX: number): void {
        this.endX = endX;
    }

    setEndY(endY: number): void {
        this.endY = endY;
    }

    private buildPathInfo(): string {
        const left = 10;
        const top = 8;
        return 'M' + (this.startX + left) + ',' + (this.startY + top) +
            ' C' + (this.startX + left) + ',' + (this.endY + top) +
            ',' + (this.startX + left) + ',' + (this.endY + top) +
            ',' + (this.endX + left) + ',' + (this.endY + top);
    }
}

// Backward-compatible global assignment
(window as unknown as Record<string, unknown>).ruleforge =
    (window as unknown as Record<string, Record<string, unknown>>).ruleforge || {};
((window as unknown as Record<string, Record<string, unknown>>).ruleforge).Connection = Connection;
