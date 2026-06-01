/**
 * DecisionTree — canvas-based decision tree editor using Raphael for SVG connections.
 *
 * Manages properties, remark, library imports, serialization, and data loading.
 */

import Context from './Context.js';
import VariableNode from './VariableNode.js';
import {
    parameterLibraries,
    variableLibraries,
    constantLibraries,
    actionLibraries,
} from '../../common/URule.js';
import { Remark } from '../../../Remark.js';

declare const ruleforge: any;

export default class DecisionTree {
    container: HTMLElement;
    topNode: VariableNode;
    properties: any[];
    remark!: Remark;
    propertyContainer!: HTMLSpanElement;
    menu!: MenuInstance;

    constructor(container: HTMLElement) {
        this.container = container;
        this.properties = [];
        this.initRemarkContainer();
        this.initPropertyContainer();

        const treeContainer = document.createElement('div');
        treeContainer.style.cssText = 'position: relative;text-align: center';
        this.container.appendChild(treeContainer);
        const context = new Context(treeContainer);
        this.topNode = new VariableNode(context, null, true);
        context.topNode = this.topNode;
        const left = 10, top = 10;
        this.topNode.nodeContainer.style.position = 'absolute';
        this.topNode.nodeContainer.style.left = left + 'px';
        this.topNode.nodeContainer.style.top = top + 'px';
    }

    private initRemarkContainer(): void {
        const remarkContainer = document.createElement('div');
        remarkContainer.style.cssText = 'margin: 5px;';
        this.container.appendChild(remarkContainer);
        this.remark = new Remark(remarkContainer);
    }

    private initPropertyContainer(): void {
        const propContainer = document.createElement('div');
        propContainer.style.cssText = 'margin: 5px;border: solid 1px #dddddd;border-radius:5px';
        this.container.appendChild(propContainer);
        this.propertyContainer = document.createElement('span');
        this.propertyContainer.style.padding = '10px';
        const addProp = document.createElement('button');
        addProp.type = 'button';
        addProp.className = 'rule-add-property btn btn-link';
        addProp.textContent = '添加属性';
        propContainer.appendChild(addProp);
        propContainer.appendChild(this.propertyContainer);
        const self = this;
        const onClick = function (menuItem: MenuItemConfig) {
            const prop = new ruleforge.RuleProperty(self, menuItem.name, menuItem.defaultValue, menuItem.editorType);
            self.addProperty(prop);
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: '优先级',
                name: 'salience',
                defaultValue: '10',
                editorType: 1,
                onClick: onClick
            }, {
                label: '生效日期',
                name: 'effective-date',
                defaultValue: '',
                editorType: 2,
                onClick: onClick
            }, {
                label: '失效日期',
                name: 'expires-date',
                defaultValue: '',
                editorType: 2,
                onClick: onClick
            }, {
                label: '是否启用',
                name: 'enabled',
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }, {
                label: '允许调试信息输出',
                name: 'debug',
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }]
        });
        addProp.addEventListener('click', function (e) {
            self.menu.show(e);
        });
    }

    addProperty(property: any): void {
        this.propertyContainer.appendChild(property.getContainer());
        this.properties.push(property);
        window._setDirty!();
    }

    toXml(): string {
        let xml = '<?xml version="1.0" encoding="UTF-8"?>';
        xml += '<decision-tree';
        for (let i = 0; i < this.properties.length; i++) {
            const prop = this.properties[i];
            xml += ' ' + prop.toXml();
        }
        xml += '>';
        xml += this.remark.toXml();
        parameterLibraries.forEach(function (item) {
            xml += '<import-parameter-library path="' + item + '"/>';
        });
        variableLibraries.forEach(function (item) {
            xml += '<import-variable-library path="' + item + '"/>';
        });
        constantLibraries.forEach(function (item) {
            xml += '<import-constant-library path="' + item + '"/>';
        });
        actionLibraries.forEach(function (item) {
            xml += '<import-action-library path="' + item + '"/>';
        });
        xml += this.topNode.toXml();
        xml += '</decision-tree>';
        return xml;
    }

    loadData(treeData: Record<string, any>): void {
        this.remark.setData(treeData['remark']);
        const salience = treeData['salience'];
        if (salience) {
            this.addProperty(new ruleforge.RuleProperty(this, 'salience', salience, 1));
        }
        const loop = treeData['loop'];
        if (loop != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'loop', loop, 3));
        }
        const effectiveDate = treeData['effectiveDate'];
        if (effectiveDate) {
            this.addProperty(new ruleforge.RuleProperty(this, 'effective-date', effectiveDate, 2));
        }
        const expiresDate = treeData['expiresDate'];
        if (expiresDate) {
            this.addProperty(new ruleforge.RuleProperty(this, 'expires-date', expiresDate, 2));
        }
        const enabled = treeData['enabled'];
        if (enabled != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'enabled', enabled, 3));
        }
        const debug = treeData['debug'];
        if (debug != null) {
            this.addProperty(new ruleforge.RuleProperty(this, 'debug', debug, 3));
        }

        this.topNode.initData(treeData['variableTreeNode']);
    }
}
