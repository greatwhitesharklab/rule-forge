import React, {Component} from 'react';
import ClickableLabel from './ClickableLabel';

interface FunctionPropertyWidgetProps {
    initialData?: {
        name?: string;
        label?: string;
    };
    onDirty?: () => void;
    ref?: React.Ref<any>;
}

interface FunctionPropertyWidgetState {
    variableName: string;
    variableLabel: string;
}

interface PropertyData {
    name?: string;
    label?: string;
    datatype?: string;
}

interface PropertyItem {
    name: string;
    label: string;
    type?: string;
}

export default class FunctionPropertyWidget extends Component<FunctionPropertyWidgetProps, FunctionPropertyWidgetState> {
    state = {
        variableName: '',
        variableLabel: '',
    };
    menu: MenuInstance | null = null;

    componentDidMount() {
        if (this.props.initialData) {
            this.setProperty(this.props.initialData);
        }
    }

    setProperty(data: PropertyData) {
        this.setState({
            variableName: data.name || '',
            variableLabel: data.label || '',
        });
        if (this.props.onDirty) this.props.onDirty();
    }

    initMenu(data?: PropertyItem[]) {
        if (!data) return;
        const self = this;
        const onClick = function (menuItem: MenuItemConfig) {
            self.setProperty({
                name: menuItem.name,
                label: menuItem.label,
                datatype: menuItem.datatype,
            });
        };
        const menuConfig: MenuConfig = {menuItems: []};
        data.forEach(function (item) {
            menuConfig.menuItems.push({
                name: item.name,
                label: item.label,
                datatype: item.type,
                onClick: onClick,
            });
        });
        if (this.menu) {
            this.menu.setConfig(menuConfig);
        } else {
            this.menu = new RuleForge.menu.Menu(menuConfig);
        }
    }

    handleLabelClick = (e: React.MouseEvent<HTMLSpanElement>) => {
        if (this.menu) this.menu.show(e);
    };

    toXml(): string {
        const {variableName, variableLabel} = this.state;
        if (!variableName) throw '请选择函数属性';
        return 'property-name="' + variableName + '" property-label="' + variableLabel + '"';
    }

    render() {
        const {variableLabel} = this.state;
        return (
            <ClickableLabel
                text={variableLabel || '选择属性'}
                color="#004C85"
                onClick={this.handleLabelClick}
            />
        );
    }
}
