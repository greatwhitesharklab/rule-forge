import Col from './Col';

interface Category {
    name: string;
    label?: string;
    variables: any[];
}

export default class AttributeCol extends Col {
    allCategories: Category[] = [];

    constructor(table: import('./ScoreCardTable').default, name: string, width: number) {
        super(table);
        this.name = name;
        this.width = width;
        this.type = 'attribute';
        this.init();
    }

    init(): void {
        const td = document.createElement('td');
        td.style.cssText = 'width: ' + this.width + 'px;padding-right: 0;background: #3c763d;color: #ffffff;border:1px solid #607D8B';
        this.td = td;
        const container = document.createElement('span');
        container.style.cursor = 'pointer';
        container.textContent = this.name;
        this.td.appendChild(container);
        this.td.appendChild(this.buildColResizeTrigger());
        this.scoreCardTable.headerRow.appendChild(this.td);
        (window as any)._VariableValueArray.push(this);
        this.bindColResize();
    }

    initMenu(data: any): void {
        this.allCategories = [];
        if (data && data.categories) {
            // 单个变量库：data 是 {categories: [...]}
            this.allCategories = data.categories;
        } else if (Array.isArray(data)) {
            // 多个变量库：data 是 [Array(10), Array(1)]，即分类数组的数组
            for (const item of data) {
                if (Array.isArray(item)) {
                    // item 是分类数组，直接合并
                    this.allCategories = this.allCategories.concat(item);
                } else if (item && item.categories) {
                    // item 是 {categories: [...]} 结构
                    this.allCategories = this.allCategories.concat(item.categories);
                }
            }
        }

        // 遍历所有AttributeCell，如果category是字符串，尝试转换为对象
        if (!this.scoreCardTable || !this.scoreCardTable.attributeRows) {
            return;
        }

        this.scoreCardTable.attributeRows.forEach((row: any) => {
            const cell = row.attributeCell;
            if (cell && cell.category && typeof cell.category === 'string') {
                const categoryName = cell.category;
                const categoryObj = this.allCategories.find(cat => cat.name === categoryName);
                if (categoryObj) {
                    cell.category = categoryObj;
                    // 更新显示
                    cell.categoryContainer.textContent = categoryObj.name;
                }
            }
            if (cell) {
                cell.updateCategoryOptions(this.allCategories);
            }
        });
    }

    // 为新创建的AttributeCell初始化category选项
    initCategoryForCell(attributeCell: any): void {
        if (this.allCategories && this.allCategories.length > 0) {
            attributeCell.updateCategoryOptions(this.allCategories);
        }
    }

    // 获取所有可用的categories
    getAllCategories(): Category[] {
        return this.allCategories || [];
    }

    toXml(): string {
        let xml = " attr-col-width=\"" + this.width + "\" attr-col-name=\"" + this.name + "\"";
        return xml;
    }
}
