import React, {Component} from 'react';
import ClickableLabel from './ClickableLabel.jsx';

export default class VariableValueWidget extends Component {
    state = {
        category: '',
        variableName: '',
        variableLabel: '',
        datatype: '',
    };
    menu = null;

    componentDidMount() {
        if (this.props.initialData) {
            this.initData(this.props.initialData);
        }
        this.initMenu();
    }

    componentDidUpdate(prevProps) {
        if (this.props.libraries !== prevProps.libraries) {
            this.initMenu();
        }
    }

    initData(data) {
        if (!data) return;
        this.setValue(data);
    }

    setValue(data) {
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

    matchAct(act) {
        if (!this.props.act) return true;
        return act && act.indexOf(this.props.act) > -1;
    }

    initMenu(libraries) {
        const data = libraries || this.props.libraries;
        if (!data) return;
        const self = this;
        const onCategoryClick = function (menuItem) {
            self.setValue({variableCategory: menuItem.label, variables: menuItem.variables});
        };
        const onVariableClick = function (menuItem) {
            self.setValue({
                variables: menuItem.parent.parent.variables,
                variableCategory: menuItem.parent.parent.label,
                variableLabel: menuItem.label,
                variableName: menuItem.name,
                datatype: menuItem.datatype,
            });
        };
        const config = {menuItems: []};
        data.forEach(function (categories) {
            (categories || []).forEach(function (category) {
                const variables = category.variables;
                const menuItem = {
                    label: category.name,
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

    handleLabelClick = (e) => {
        if (this.menu) this.menu.show(e);
    };

    toXml() {
        const {category, variableName, variableLabel, datatype} = this.state;
        if (!category) throw '变量不能为空！';
        let xml = 'var-category="' + category + '"';
        if (variableName) {
            xml += ' var="' + variableName + '" var-label="' + variableLabel + '" datatype="' + datatype + '"';
        }
        return xml;
    }

    getType() {
        return this.state.variableName ? 'Variable' : 'VariableCategory';
    }

    getDisplayLabel() {
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
