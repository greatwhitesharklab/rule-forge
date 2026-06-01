import React, {Component} from 'react';
import ClickableLabel from './ClickableLabel';

interface VariableValueWidgetProps {
    initialData?: VariableValueData;
    libraries?: VariableCategoryGroup[];
    onDirty?: () => void;
    onFunctionPropertyUpdate?: (variables: VariableItem[]) => void;
    act?: string;
    ref?: React.Ref<any>;
}

interface VariableValueWidgetState {
    category: string;
    variableName: string;
    variableLabel: string;
    datatype: string;
}

interface VariableValueData {
    variableCategory?: string;
    variableName?: string;
    variableLabel?: string;
    datatype?: string;
    variables?: VariableItem[];
}

interface VariableItem {
    name: string;
    label: string;
    type?: string;
    act?: string;
}

interface VariableCategory {
    name: string;
    variables: VariableItem[];
}

type VariableCategoryGroup = VariableCategory[];

export default class VariableValueWidget extends Component<VariableValueWidgetProps, VariableValueWidgetState> {
    state = {
        category: '',
        variableName: '',
        variableLabel: '',
        datatype: '',
    };
    menu: MenuInstance | null = null;

    componentDidMount() {
        if (this.props.initialData) {
            this.initData(this.props.initialData);
        }
        this.initMenu();
    }

    componentDidUpdate(prevProps: VariableValueWidgetProps) {
        if (this.props.libraries !== prevProps.libraries) {
            this.initMenu();
        }
    }

    initData(data: VariableValueData) {
        if (!data) return;
        this.setValue(data);
    }

    setValue(data: VariableValueData) {
        this.setState({
            category: data.variableCategory || '',
            variableName: data.variableName || '',
            variableLabel: data.variableLabel || '',
            datatype: data.datatype || '',
        });
        if (this.props.onFunctionPropertyUpdate && data.variables) {
            this.props.onFunctionPropertyUpdate(data.variables);
        }
        if (this.props.onDirty) this.props.onDirty();
    }

    matchAct(act?: string): boolean {
        if (!this.props.act) return true;
        return !!act && act.indexOf(this.props.act) > -1;
    }

    initMenu(libraries?: VariableCategoryGroup[]) {
        const data = libraries || this.props.libraries;
        if (!data) return;
        const self = this;
        const onCategoryClick = function (menuItem: MenuItemConfig) {
            self.setValue({variableCategory: menuItem.label, variables: menuItem.variables as VariableItem[]});
        };
        const onVariableClick = function (menuItem: MenuItemConfig) {
            self.setValue({
                variables: (menuItem.parent!.parent!.variables as VariableItem[]),
                variableCategory: menuItem.parent!.parent!.label,
                variableLabel: menuItem.label,
                variableName: menuItem.name,
                datatype: menuItem.datatype,
            });
        };
        const config: MenuConfig = {menuItems: []};
        data.forEach(function (categories) {
            (categories || []).forEach(function (category) {
                const variables = category.variables;
                const menuItem: MenuItemConfig = {
                    label: category.name,
                    name: '',
                    variables: variables,
                    onClick: onCategoryClick,
                };
                (variables || []).forEach(function (variable) {
                    if (self.matchAct(variable.act)) {
                        if (!menuItem.subMenu) {
                            menuItem.subMenu = {menuItems: []};
                        }
                        menuItem.subMenu.menuItems.push({
                            name: variable.name,
                            label: variable.label,
                            datatype: variable.type,
                            act: variable.act,
                            variables: variables,
                            onClick: onVariableClick,
                        });
                    }
                });
                config.menuItems.push(menuItem);
            });
        });
        if (self.menu) {
            self.menu.setConfig(config);
        } else {
            self.menu = new RuleForge.menu.Menu(config);
        }
    }

    handleLabelClick = (e: React.MouseEvent<HTMLSpanElement>) => {
        if (this.menu) this.menu.show(e);
    };

    toXml(): string {
        const {category, variableName, variableLabel, datatype} = this.state;
        if (!category) throw '变量不能为空！';
        let xml = 'var-category="' + category + '"';
        if (variableName) {
            xml += ' var="' + variableName + '" var-label="' + variableLabel + '" datatype="' + datatype + '"';
        }
        return xml;
    }

    getType(): string {
        return this.state.variableName ? 'Variable' : 'VariableCategory';
    }

    getDisplayLabel(): string | null {
        const {category, variableLabel} = this.state;
        if (!category) return null;
        if (variableLabel) return category + '.' + variableLabel;
        return category;
    }

    render() {
        const label = this.getDisplayLabel();
        return (
            <ClickableLabel
                text={label || '请选择变量'}
                color={label ? 'darkcyan' : undefined}
                onClick={this.handleLabelClick}
            />
        );
    }
}
