declare const ruleforge: any;

export class Connection {
    isJoin: boolean | string;
    context: any;
    parentJoin: any;
    startX: number = 0;
    startY: number = 0;
    endX: number = 0;
    endY: number = 0;
    path: any;
    join?: any;
    condition?: any;
    conditionContainer?: HTMLDivElement;

    constructor(context: any, isJoin: boolean | string, parentJoin: any) {
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
            this.initJoin(this.isJoin);
        } else {
            this.initCondition();
        }
    }

    toXml(): string {
        let xml = '';
        if (this.isJoin) {
            xml = this.join.toXml();
        } else {
            xml = this.condition.toXml();
        }
        return xml;
    }

    private initJoin(joinType: boolean | string): void {
        if (joinType === 'named') {
            this.join = new ruleforge.NamedJoin(this.context);
        } else {
            this.join = new ruleforge.Join(this.context);
        }
        this.join.init(this);
        const joinContainer = this.join.getContainer();
        const left = (this.endX + 10) + 'px';
        const top = this.endY + 'px';
        joinContainer.style.position = 'absolute';
        joinContainer.style.left = left;
        joinContainer.style.top = top;
        this.context.getCanvas().appendChild(joinContainer);
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
        if (this.parentJoin instanceof ruleforge.NamedJoin) {
            this.condition = new ruleforge.NamedCondition(this.context, this.conditionContainer, this.parentJoin);
        } else {
            this.condition = new ruleforge.Condition(this.conditionContainer);
        }
        const del = document.createElement('i');
        del.className = 'glyphicon glyphicon-trash';
        del.style.color = '#019dff';
        del.style.cursor = 'pointer';
        del.style.fontSize = '9pt';
        del.style.paddingLeft = '5px';
        const self = this;
        del.addEventListener('click', function () {
            self.parentJoin.removeConnection(self);
        });
        this.conditionContainer.appendChild(del);
        this.context.getCanvas().appendChild(this.conditionContainer);
    }

    update(add: boolean | null): void {
        const pathInfo = this.buildPathInfo();
        this.path.attr('path', pathInfo);
        if (add === null) {
            const left = (this.endX + 10) + 'px';
            if (this.conditionContainer) {
                this.conditionContainer.style.left = left;
            } else {
                this.join.getContainer().style.left = left;
            }
        } else {
            const top = this.endY + 'px';
            if (this.conditionContainer) {
                this.conditionContainer.style.top = top;
            } else {
                this.join.getContainer().style.top = top;
            }
        }
        if (this.join) {
            this.join.resetItemPosition(0, add);
        }
    }

    getParentJoin(): any {
        return this.parentJoin;
    }

    getCondition(): any {
        return this.condition;
    }

    getJoin(): any {
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

(ruleforge as any).Connection = Connection;
