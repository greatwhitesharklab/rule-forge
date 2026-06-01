/**
 * Context — creates the Raphael canvas and provides factory methods for node types.
 */

import Raphael from 'raphael';
import type { RaphaelPaper } from 'raphael';
import VariableNode from './VariableNode.js';
import ActionNode from './ActionNode.js';
import ConditionNode from './ConditionNode.js';

export default class Context {
    container: HTMLElement;
    paper: RaphaelPaper;
    topNode?: any;
    lastLeftNode?: any;
    lastBottomNode?: any;

    constructor(container: HTMLElement) {
        this.container = container;
        this.paper = new (Raphael as any)(container, '100%', '100%');
    }

    newVariableNode(parentNode: any): VariableNode {
        return new VariableNode(this, parentNode);
    }

    newActionNode(parentNode: any): ActionNode {
        return new ActionNode(this, parentNode);
    }

    newConditionNode(parentNode: any): ConditionNode {
        return new ConditionNode(this, parentNode);
    }
}
