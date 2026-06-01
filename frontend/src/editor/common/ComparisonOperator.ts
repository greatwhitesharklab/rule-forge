import { renderReact } from '../../components/react-bridge.js';
import ComparisonOperatorWidget from '../../components/widgets/ComparisonOperatorWidget.jsx';
import { InputType } from './InputType';

interface OperatorConfig {
    label: string;
    endInfo?: string;
    noInput?: boolean;
}

const OPERATORS: Record<string, OperatorConfig> = {
    GreaterThen: { label: '大于' },
    GreaterThenEquals: { label: '大于或等于' },
    LessThen: { label: '小于' },
    LessThenEquals: { label: '小于或等于' },
    Equals: { label: '等于' },
    EqualsIgnoreCase: { label: '等于(不分大小写)' },
    StartWith: { label: '开始于' },
    NotStartWith: { label: '不开始于' },
    EndWith: { label: '结束于' },
    NotEndWith: { label: '不结束于' },
    NotEquals: { label: '不等于' },
    NotEqualsIgnoreCase: { label: '不等于(不分大小写)' },
    In: { label: '在集合', endInfo: '之中' },
    NotIn: { label: '不在集合', endInfo: '之中' },
    Null: { label: '为空', noInput: true },
    NotNull: { label: '不为空', noInput: true },
    Match: { label: '匹配正则表达式' },
    NotMatch: { label: '不匹配正则表达式' },
    Contain: { label: '包含' },
    NotContain: { label: '不包含' },
};

export class ComparisonOperator {
    container: HTMLSpanElement;
    inputType: InputType | null = null;
    operatorName: string = '';
    widgetRef: any = null;
    _pendingOperator: string | null = null;
    menuCallFun: (() => void) | null;

    constructor(menuCallFun?: (() => void) | null) {
        this.container = document.createElement('span');
        this.menuCallFun = menuCallFun || null;
        const self = this;
        renderReact(ComparisonOperatorWidget, {
            onMenuHide: function () {
                if (self.menuCallFun) self.menuCallFun();
            },
            onSelect: function (name: string) {
                self._applyOperator(name);
            },
            ref: function (ref: any) {
                self.widgetRef = ref;
                if (self._pendingOperator) {
                    ref.setOperator(self._pendingOperator);
                    self._pendingOperator = null;
                }
            },
        }, this.container);
    }

    _applyOperator(operator: string): void {
        const config = OPERATORS[operator];
        if (!config) return;
        this.operatorName = operator;
        if (this.inputType) {
            this.inputType.getContainer().remove();
            this.inputType = null;
        }
        if (!config.noInput) {
            this.inputType = new InputType(config.endInfo || null);
        }
        if (this.widgetRef) {
            this.widgetRef.setOperator(operator);
        } else {
            this._pendingOperator = operator;
        }
        if (window._setDirty) window._setDirty();
    }

    initRightValue(data: Record<string, any>): void {
        if (!this.inputType) {
            return;
        }
        this.inputType.setValueType(data['valueType'], data);
    }

    setOperator(operator: string): void {
        this._applyOperator(operator);
    }

    getOperator(): string {
        if (!this.operatorName) {
            throw '请选择比较操作符！';
        }
        return this.operatorName;
    }

    getInputType(): InputType | null {
        return this.inputType;
    }

    getContainer(): HTMLSpanElement {
        return this.container;
    }
}

// Backward-compatible global registration
(ruleforge as Record<string, any>).ComparisonOperator = ComparisonOperator;
