import Cell from './Cell';
import { generateContainer } from '../editor/common/URule.js';

interface Variable {
    name: string;
    label: string;
    type: string;
}

interface Category {
    name: string;
    label?: string;
    variables: Variable[];
}

declare const ruleforge: {
    RuleProperty: new (config: any, name: string, defaultValue: any, editorType: number) => {
        getContainer(): HTMLElement;
        toXml(): string;
    };
};

export default class AttributeCell extends Cell {
    category: Category | string | null = null;
    declare categoryName?: string;
    variable?: Variable;
    categorySelector!: HTMLSpanElement;
    categoryContainer!: HTMLElement;
    categoryMenuDef!: HTMLUListElement;
    propContainer!: HTMLElement;
    weightContainer!: HTMLDivElement;
    weightEditor!: HTMLInputElement;

    constructor(row: import('./Row').default, col: import('./Col').default, cellData?: any) {
        super(row, col, cellData);
        this.type = "attribute";
    }

    initCell(cellData?: any): void {
        const container = document.createElement('div');
        this.td.appendChild(container);

        // Category选择器
        const categoryContainer = document.createElement('div');
        categoryContainer.style.cssText = 'margin-bottom: 5px;';
        container.appendChild(categoryContainer);
        const categoryLabel = document.createElement('label');
        categoryLabel.style.cssText = 'margin-right: 5px; color: #666;';
        categoryLabel.textContent = '分类：';
        categoryContainer.appendChild(categoryLabel);
        this.categorySelector = document.createElement('span');
        this.categorySelector.className = 'dropdown';
        this.categorySelector.style.cursor = 'pointer';
        categoryContainer.appendChild(this.categorySelector);
        const categorySpan = document.createElement('span');
        categorySpan.className = 'dropdown-toggle';
        categorySpan.setAttribute('data-toggle', 'dropdown');
        categorySpan.style.cssText = 'color: #3c763d;font-weight: bold;font-size: 11px';
        this.categorySelector.appendChild(categorySpan);
        this.categoryContainer = document.createElement('span');
        this.categoryContainer.textContent = "选择分类";
        categorySpan.appendChild(this.categoryContainer);
        categorySpan.appendChild(document.createTextNode(' '));
        const caret = document.createElement('span');
        caret.className = 'caret';
        categorySpan.appendChild(caret);
        this.categoryMenuDef = document.createElement('ul');
        this.categoryMenuDef.className = 'dropdown-menu';
        this.categoryMenuDef.setAttribute('role', 'menu');
        this.categorySelector.appendChild(this.categoryMenuDef);

        this.propContainer = generateContainer();
        if (cellData) {
            this.variableLabel = cellData.variableLabel;
            this.variableName = cellData.variableName;
            this.weight = cellData.weight;

            // 加载 datatype 字段
            this.datatype = cellData.datatype;

            // 处理category字段，可能是category或variableCategory
            const categoryValue = cellData.category || cellData.variableCategory;
            if (categoryValue) {
                this.category = categoryValue;
                // 处理category可能是字符串或对象的情况
                const catName = typeof categoryValue === 'string' ? categoryValue : categoryValue.name;
                this.categoryName = catName;
                this.categoryContainer.textContent = catName;
            }
            this.propContainer.textContent = this.variableLabel || this.variableName || '';
        } else {
            this.propContainer.textContent = "请选择属性";
            console.log('AttributeCell init: no cellData provided');
        }
        container.appendChild(this.propContainer);
        this.propContainer.style.cssText = 'color: green';
        const _this = this;
        const del = document.createElement('span');
        del.className = 'attribute-operation';
        del.style.cssText = 'color: #ff0600';
        del.innerHTML = '<i class="glyphicon glyphicon-remove" style="cursor: pointer" title="删除当前属性行"/>';
        container.appendChild(del);
        del.addEventListener('click', function () {
            window.bootbox.confirm("真的要删除？", function (result) {
                if (!result) return;
                (_this.row as any).remove();
            });
        });
        const addCondition = document.createElement('span');
        addCondition.className = 'attribute-operation';
        addCondition.style.cssText = 'color: #019dff';
        addCondition.innerHTML = '<i class="glyphicon glyphicon-plus-sign" style="cursor: pointer" title="添加条件行"/>';
        container.appendChild(addCondition);
        addCondition.addEventListener('click', function () {
            (_this.row as import('./AttributeRow').default).addConditionRow();
        });
        // 初始化时不自动加载属性菜单，需要先选择category
        this.weightContainer = document.createElement('div');
        this.weightContainer.style.cssText = 'margin-top: 5px;color:#999';
        this.weightContainer.innerHTML = '<label>权重：</label>';
        if (!this.row.scoreCardTable.weightSupport) {
            this.weightContainer.style.display = 'none';
        }

        this.weightEditor = document.createElement('input');
        this.weightEditor.type = 'text';
        this.weightEditor.className = 'form-control';
        this.weightEditor.style.cssText = 'width:60px;height: 25px;display: inline-block';
        this.weightContainer.appendChild(this.weightEditor);
        if (this.weight) {
            this.weightEditor.value = this.weight;
        }
        this.weightEditor.addEventListener('change', function () {
            _this.weight = (this as HTMLInputElement).value;
        });
        container.appendChild(this.weightContainer);

        // 通过AttributeCol初始化category选项
        (this.col as import('./AttributeCol').default).initCategoryForCell(this);
    }

    // 更新category选项
    updateCategoryOptions(categories: Category[]): void {
        const _this = this;
        this.categoryMenuDef.innerHTML = '';

        if (!categories || categories.length === 0) {
            return;
        }

        for (const category of categories) {
            const categoryName = category.name || category.label || 'Unknown Category';
            const menuItem = document.createElement('li');
            menuItem.innerHTML = '<a href="###">' + categoryName + '</a>';
            this.categoryMenuDef.appendChild(menuItem);
            menuItem.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                _this.category = category;
                _this.categoryContainer.textContent = categoryName;
                // 更新属性选项
                _this.refreshAttributeCellMenus(category.variables || []);
                // 清空当前选择的属性
                _this.variable = undefined;
                _this.variableName = undefined;
                _this.variableLabel = undefined;
                _this.datatype = undefined;
                _this.propContainer.textContent = "请选择属性";
                // 手动关闭dropdown
                _this.categorySelector.classList.remove('open');
            });
        }

        // 确保Bootstrap dropdown正确初始化
        if (this.categorySelector && !this.categorySelector.classList.contains('dropdown-initialized')) {
            this.categorySelector.classList.add('dropdown-initialized');
            // 手动绑定dropdown toggle事件
            const toggle = this.categorySelector.querySelector('.dropdown-toggle');
            toggle!.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                _this.categorySelector.classList.toggle('open');
            });

            // 点击其他地方关闭dropdown
            document.addEventListener('click', function handler(e) {
                if (!_this.categorySelector.contains(e.target as Node)) {
                    _this.categorySelector.classList.remove('open');
                }
            });
        }
    }

    // 获取当前选择的category名称
    getCategoryName(): string | null {
        if (!this.category) return null;
        return typeof this.category === 'string' ? this.category : this.category.name;
    }

    showWeight(): void {
        this.weightContainer.style.display = '';
        this.weight = undefined;
        this.weightEditor.value = '';
    }

    hideWeight(): void {
        this.weightContainer.style.display = 'none';
        this.weight = undefined;
        this.weightEditor.value = '';
    }

    refreshAttributeCellMenus(variables: Variable[]): void {
        if (!variables || variables.length === 0) {
            return;
        }

        const menuItems: MenuItemConfig[] = [];
        const _this = this;
        for (const variable of variables) {
            menuItems.push({
                label: variable.label || variable.name,
                onClick: function () {
                    _this.variable = variable;
                    _this.variableName = variable.name;
                    _this.variableLabel = variable.label;
                    _this.datatype = variable.type;
                    _this.propContainer.textContent = variable.label || variable.name;
                    // 更新propContainer的颜色以表示已选择
                    _this.propContainer.style.color = 'green';
                }
            });
        }
        if (!(this.propContainer as any).menu) {
            (this.propContainer as any).menu = new RuleForge.menu.Menu({ menuItems });
            this.propContainer.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                ((<any>_this.propContainer).menu as MenuInstance).show(e as unknown as MouseEvent);
            });
        } else {
            ((this.propContainer as any).menu as MenuInstance).setConfig({ menuItems });
        }
    }
}
