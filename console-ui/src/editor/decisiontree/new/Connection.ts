/**
 * Connection — draws an SVG path between parent and child nodes using Raphael.
 */

import Raphael from 'raphael';
import type { RaphaelPath } from 'raphael';

export default class Connection {
    context: any;
    nodeType: string;
    parentNode: any;
    startX: number = 0;
    startY: number = 0;
    endX: number = 0;
    endY: number = 0;
    path: RaphaelPath | null = null;
    node: any = null;
    isJoin?: boolean;

    constructor(context: any, nodeType: string, parentNode: any) {
        this.context = context;
        this.nodeType = nodeType;
        this.parentNode = parentNode;
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
        const color = Raphael.rgb(120, 120, 120);
        this.path = this.context.paper.path(this.buildPathInfo()).attr('stroke', color);
        if (this.nodeType === 'condition') {
            this.node = this.context.newConditionNode(this.parentNode);
        } else if (this.nodeType === 'action') {
            this.node = this.context.newActionNode(this.parentNode);
        } else if (this.nodeType === 'variable') {
            this.node = this.context.newVariableNode(this.parentNode);
        }
        this.node.connection = this;
        this.initNodePosition();
    }

    updatePath(): void {
        const pathInfo = this.buildPathInfo();
        this.path!.attr('path', pathInfo);
    }

    update(nodeHeight: number): void {
        this.updatePath();
        const top = parseInt(this.node.nodeContainer.style.top) + nodeHeight;
        this.node.nodeContainer.style.top = top + 'px';
        this.node.resetItemPosition(0, nodeHeight);
    }

    initNodePosition(): void {
        const h = this.node.nodeContainer.offsetHeight;
        const w = this.node.nodeContainer.offsetWidth;
        const left = this.endX;
        const top = this.endY - h / 2;
        this.node.nodeContainer.style.position = 'absolute';
        this.node.nodeContainer.style.left = left + 'px';
        this.node.nodeContainer.style.top = top + 'px';
    }

    buildPathInfo(): string {
        return 'M' + this.startX + ',' + this.startY +
            ' C' + this.startX + ',' + this.endY +
            ',' + this.startX + ',' + this.endY +
            ',' + this.endX + ',' + this.endY;
    }
}
