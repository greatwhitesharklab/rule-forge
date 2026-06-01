import Raphael from 'raphael';

type PaperLike = ReturnType<typeof Raphael>;

export class Context {
    container: HTMLElement;
    paper: PaperLike;
    rule: any;
    rootJoin: any = null;

    constructor(container: HTMLElement, rule: any) {
        this.container = container;
        this.paper = new (Raphael as any)(this.container, '100%', '100%');
        this.rule = rule;
    }

    putToNamedMap(referenceName: string, variableCategory: any): void {
        this.rule.namedMap.set(referenceName, variableCategory);
    }

    deleteFromNamedMap(referenceName: string): void {
        this.rule.namedMap.delete(referenceName);
    }

    getVariableCategoryFromNamedMap(referenceName: string): any {
        return this.rule.namedMap.get(referenceName);
    }

    getCanvas(): HTMLElement {
        return this.container;
    }

    getPaper(): PaperLike {
        return this.paper;
    }

    setRootJoin(join: any): void {
        this.rootJoin = join;
    }

    getRootJoin(): any {
        return this.rootJoin;
    }

    getTotalChildrenCount(): number {
        return this.rootJoin.getChildrenCount();
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).Context = Context;
