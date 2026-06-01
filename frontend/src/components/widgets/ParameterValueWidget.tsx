import React, {Component} from 'react';
import ClickableLabel from './ClickableLabel';

interface ParameterValueWidgetProps {
    initialData?: ParameterValueData;
    libraries?: ParameterVariableGroup[];
    onDirty?: () => void;
    act?: string;
}

interface ParameterValueWidgetState {
    parameterName: string;
    parameterLabel: string;
    datatype: string;
}

interface ParameterValueData {
    variableName?: string;
    variableLabel?: string;
    datatype?: string;
}

interface ParameterVariable {
    name: string;
    label: string;
    type?: string;
    act?: string;
}

type ParameterVariableGroup = ParameterVariable[];

export default class ParameterValueWidget extends Component<ParameterValueWidgetProps, ParameterValueWidgetState> {
    state = {
        parameterName: '',
        parameterLabel: '',
        datatype: '',
    };
    menu: MenuInstance | null = null;

    componentDidMount() {
        if (this.props.initialData) {
            this.initData(this.props.initialData);
        }
        this.initMenu();
    }

    componentDidUpdate(prevProps: ParameterValueWidgetProps) {
        if (this.props.libraries !== prevProps.libraries) {
            this.initMenu();
        }
    }

    initData(data: ParameterValueData) {
        if (!data) return;
        this.setValue(data);
    }

    setValue(data: ParameterValueData) {
        this.setState({
            parameterName: data.variableName || '',
            parameterLabel: data.variableLabel || '',
            datatype: data.datatype || '',
        });
        if (this.props.onDirty) this.props.onDirty();
    }

    matchAct(act?: string): boolean {
        if (!this.props.act) return true;
        return !!act && act.indexOf(this.props.act) > -1;
    }

    initMenu(libraries?: ParameterVariableGroup[]) {
        const data = libraries || this.props.libraries;
        if (!data) return;
        const self = this;
        const onClick = function (menuItem: MenuItemConfig) {
            self.setValue({
                variableName: menuItem.name,
                variableLabel: menuItem.label,
                datatype: menuItem.datatype,
            });
        };
        const config: MenuConfig = {menuItems: []};
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

    handleLabelClick = (e: React.MouseEvent<HTMLSpanElement>) => {
        if (this.menu) this.menu.show(e);
    };

    toXml(): string {
        const {parameterLabel, parameterName, datatype} = this.state;
        if (!parameterLabel) throw '参数不能为空！';
        return ' var-category="参数" var="' + parameterName + '" var-label="' + parameterLabel + '" datatype="' + datatype + '"';
    }

    getDisplayLabel(): string | null {
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
