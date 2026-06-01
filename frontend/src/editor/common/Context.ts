import Raphael from 'raphael';

export class Context {
    container: HTMLElement;
    paper: RaphaelPaper;
    rule: any;
    rootJoin: any = null;

    constructor(container: HTMLElement, rule: any) {
        this.container = container;
        this.paper = new Raphael(this.container, '100%', '100%') as unknown as RaphaelPaper;
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

    getPaper(): RaphaelPaper {
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

// Raphael paper type (minimal)
interface RaphaelPaper {
    [key: string]: any;
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).Context = Context;
