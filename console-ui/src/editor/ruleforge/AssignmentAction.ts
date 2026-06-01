/**
 * AssignmentAction — placeholder for variable assignment action.
 *
 * The original JS file was empty (0 bytes). This module provides a stub
 * that gets wired up via the ruleforge namespace at runtime.
 */

declare const ruleforge: any;

export class AssignmentAction {
    container: HTMLElement;

    constructor(rule?: any) {
        this.container = document.createElement('span');
    }

    initData(data?: any): void {
        // No-op stub
    }

    toXml(): string {
        return '';
    }

    getContainer(): HTMLElement {
        return this.container;
    }
}

(ruleforge as any).AssignmentAction = AssignmentAction;
