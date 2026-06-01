/**
 * BaseNode — base class for the canvas-based decision tree nodes.
 *
 * Handles child management, SVG connection drawing via Raphael, resize,
 * repositioning, and canvas size resets.
 */

import Connection from './Connection.js';

export default class BaseNode {
    context: any;
    parentNode: BaseNode | null;
    children: Connection[];
    nodeContainer!: HTMLDivElement;
    nodeHeight?: number;
    nodeWidth?: number;
    connection?: Connection;

    constructor(context: any, parentNode: BaseNode | null) {
        this.context = context;
        this.parentNode = parentNode;
        this.children = [];
    }

    initBindResizeEvent(): void {
        const self = this;
        this.nodeContainer.addEventListener('DOMSubtreeModified', function () {
            self.resizeNode();
        });
    }

    initChildrenNodeData(data: Record<string, any>): void {
        let childrenNodes: any[] = [];
        const conditionTreeNodes = data['conditionTreeNodes'];
        if (conditionTreeNodes) {
            childrenNodes = conditionTreeNodes;
        }
        const variableTreeNodes = data['variableTreeNodes'];
        if (variableTreeNodes) {
            childrenNodes = childrenNodes.concat(variableTreeNodes);
        }
        const actionTreeNodes = data['actionTreeNodes'];
        if (actionTreeNodes) {
            childrenNodes = childrenNodes.concat(actionTreeNodes);
        }
        if (!childrenNodes || childrenNodes.length === 0) {
            return;
        }
        for (let i = 0; i < childrenNodes.length; i++) {
            const childNodeData = childrenNodes[i];
            const newNode = this.addChild(childNodeData['nodeType']);
            newNode.initData(childNodeData);
        }
    }

    delete(): void {
        while (this.children.length > 0) {
            const connection = this.children[this.children.length - 1];
            connection.node.delete();
        }
        let nodeHeight = 0;
        if (this.children.length > 1) {
            for (let i = 0; i < this.children.length; i++) {
                const node = this.children[i].node.nodeContainer as HTMLElement;
                nodeHeight += node.offsetHeight + 15;
            }
        } else {
            nodeHeight = this.nodeContainer.offsetHeight + 15;
        }
        this.nodeContainer.remove();
        this.connection!.path.remove();
        const parentChildren = this.parentNode!.children;
        const pos = parentChildren.indexOf(this.connection!);
        parentChildren.splice(pos, 1);
        this.parentNode!.resetItemPosition(pos, -nodeHeight);
        this.parentNode!.resetParentNodePosition();
        this.resetCanvasSize();
        window._setDirty!();
    }

    addChild(nodeType: string): BaseNode {
        const parentLeft = parseInt(this.nodeContainer.style.left) + 3;
        const parentTop = parseInt(this.nodeContainer.style.top) + 3;
        const conn = new Connection(this.context, nodeType, this);
        const childrenCount = this.children.length;
        const w = this.nodeContainer.offsetWidth;
        const h = this.nodeContainer.offsetHeight;
        let startX = parentLeft + w - 20;
        if (this.children.length > 0) {
            const firstConn = this.children[0];
            startX = firstConn.startX;
        }
        let startY = parentTop + h / 2;
        let lastNodeYPosition = 0;
        const lastNode = this.findLastChildrenNode();
        if (lastNode) {
            const lastNodeContainer = lastNode.nodeContainer;
            lastNodeYPosition = parseInt(lastNodeContainer.style.top) + lastNodeContainer.offsetHeight + 15;
            startY = lastNode.connection!.startX;
        }
        let endX = startX + 40;
        if (this.children.length > 0) {
            const firstConn = this.children[0];
            endX = firstConn.endX;
        }
        let endY = startY;
        if (lastNodeYPosition > 0) {
            endY = lastNodeYPosition + h / 2 + 3;
        }
        conn.drawPath(startX, startY, endX, endY);
        this.children.push(conn);
        if (childrenCount > 0) {
            if (this.parentNode) {
                const newNodeHeight = conn.node.nodeContainer.offsetHeight + 15;
                const parentChildren = this.parentNode.children;
                const pos = parentChildren.indexOf(this.connection!);
                this.parentNode.resetItemPosition(pos + 1, newNodeHeight);
            }
            this.resetParentNodePosition();
        }
        if (this.context.lastLeftNode) {
            const left = parseInt(conn.node.nodeContainer.style.left);
            const oldLeft = parseInt(this.context.lastLeftNode.nodeContainer.style.left);
            if (left > oldLeft) {
                this.context.lastLeftNode = conn.node;
            }
        } else {
            this.context.lastLeftNode = conn.node;
        }
        if (this.context.lastBottomNode) {
            const top = parseInt(conn.node.nodeContainer.style.top);
            const oldTop = parseInt(this.context.lastBottomNode.nodeContainer.style.top);
            if (top > oldTop) {
                this.context.lastBottomNode = conn.node;
            }
        } else {
            this.context.lastBottomNode = conn.node;
        }

        this.resetCanvasSize();
        window._setDirty!();
        return conn.node;
    }

    resizeNode(): void {
        if (!this.nodeHeight) {
            return;
        }
        const nodeHeight = this.nodeContainer.offsetHeight + 15;
        if (this.parentNode && nodeHeight !== this.nodeHeight) {
            const difHeight = nodeHeight - this.nodeHeight;
            const pos = this.parentNode.children.indexOf(this.connection!);
            this.parentNode.resetItemPosition(pos + 1, difHeight);
            this.resetParentNodePosition();
            this.nodeHeight = nodeHeight;
        }
        const nodeWidth = this.nodeContainer.offsetWidth;
        if (nodeWidth !== this.nodeWidth) {
            const difWidth = nodeWidth - this.nodeWidth!;
            this.resetItemWidth(difWidth);
            this.nodeWidth = nodeWidth;
        }
        this.resetCanvasSize();
    }

    resetCanvasSize(): void {
        if (this.context.lastLeftNode) {
            const left = parseInt(this.context.lastLeftNode.nodeContainer.style.left);
            this.context.container.style.width = (left + this.context.lastLeftNode.nodeContainer.offsetWidth + 30) + 'px';
        }
        if (this.context.lastBottomNode) {
            const top = parseInt(this.context.lastBottomNode.nodeContainer.style.top);
            this.context.container.style.height = (top + this.context.lastBottomNode.nodeContainer.offsetHeight + 30) + 'px';
        }
    }

    resetItemWidth(difWidth: number): void {
        for (let i = 0; i < this.children.length; i++) {
            const connection = this.children[i];
            connection.startX = connection.startX + difWidth;
            connection.endX = connection.endX + difWidth;
            connection.updatePath();
            const node = connection.node;
            const left = parseInt(node.nodeContainer.style.left) + difWidth;
            node.nodeContainer.style.left = left + 'px';
            node.resetItemWidth(difWidth);
        }
    }

    resetItemPosition(index: number, nodeHeight: number): void {
        if (index === -1) {
            return;
        }
        for (let i = index; i < this.children.length; i++) {
            const connection = this.children[i];
            connection.endY = connection.endY + nodeHeight;
            if (index === 0) {
                connection.startY = connection.startY + nodeHeight;
            }
            connection.update(nodeHeight);
        }
        if (index > 0 && this.parentNode) {
            const parentChildren = this.parentNode.children;
            const pos = parentChildren.indexOf(this.connection!);
            this.parentNode.resetItemPosition(pos + 1, nodeHeight);
        }
    }

    resetParentNodePosition(): void {
        let newTop: number | undefined;
        if (this.children.length > 1) {
            const firstNodeContainer = this.children[0].node.nodeContainer;
            const lastNodeContainer = this.children[this.children.length - 1].node.nodeContainer;
            const firstNodeTop = parseInt(firstNodeContainer.style.top);
            const lastNodeTop = parseInt(lastNodeContainer.style.top) + lastNodeContainer.offsetHeight;
            const dif = lastNodeTop - firstNodeTop;
            newTop = parseInt(firstNodeContainer.style.top) + dif / 2 - this.nodeContainer.offsetHeight / 2;
        } else if (this.children.length === 1) {
            const firstNodeContainer = this.children[0].node.nodeContainer;
            newTop = parseInt(firstNodeContainer.style.top);
        }
        if (newTop !== undefined) {
            this.nodeContainer.style.top = (newTop - 2) + 'px';
            if (this.connection) {
                this.connection.endY = newTop + this.nodeContainer.offsetHeight / 2;
                this.connection.updatePath();
            }
            if (this.children.length === 1) {
                newTop = this.children[0].endY;
            } else {
                newTop += this.nodeContainer.offsetHeight / 2;
            }
            for (let i = 0; i < this.children.length; i++) {
                const connection = this.children[i];
                connection.startY = newTop!;
                connection.updatePath();
            }
        }
        if (this.parentNode) {
            this.parentNode.resetParentNodePosition();
        }
    }

    findLastChildrenNode(): BaseNode | null {
        if (this.children.length === 0) {
            return null;
        }
        const lastNode = this.children[this.children.length - 1].node;
        const nextLastNode = lastNode.findLastChildrenNode();
        if (nextLastNode) {
            return nextLastNode;
        }
        return lastNode;
    }

    initData(_data: Record<string, any>): void {
        // Override in subclasses
    }

    toXml(): string {
        return '';
    }
}
