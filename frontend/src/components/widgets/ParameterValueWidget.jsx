import React, {Component} from 'react';
import ClickableLabel from './ClickableLabel.jsx';

export default class ParameterValueWidget extends Component {
    state = {
        parameterName: '',
        parameterLabel: '',
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
            parameterName: data.variableName || '',
            parameterLabel: data.variableLabel || '',
            datatype: data.datatype || '',
        });
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
        const onClick = function (menuItem) {
            self.setValue({
                variableName: menuItem.name,
                variableLabel: menuItem.label,
                datatype: menuItem.datatype,
            });
        };
        const config = {menuItems: []};
        data.forEach(function (variables) {
            (variables || []).forEach(function (variable) {
                if (self.matchAct(variable.act)) {
                    config.menuItems.push({
                        name: variable.name,
                        label: variable.label,
                        datatype: variable.type,
                        act: variable.act,
                        onClick: onClick,
                    });
                }
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
        const {parameterLabel, parameterName, datatype} = this.state;
        if (!parameterLabel) throw '参数不能为空！';
        return ' var-category="参数" var="' + parameterName + '" var-label="' + parameterLabel + '" datatype="' + datatype + '"';
    }

    getDisplayLabel() {
        const {parameterLabel} = this.state;
        if (parameterLabel) return '参数.' + parameterLabel;
        return null;
    }

    render() {
        const label = this.getDisplayLabel();
        return (
            <ClickableLabel
                text={label || '请选择参数'}
                color={label ? '#6b3db0' : undefined}
                onClick={this.handleLabelClick}
            />
        );
    }
}
