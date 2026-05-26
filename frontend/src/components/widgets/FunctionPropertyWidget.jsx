import React, {Component} from 'react';
import ClickableLabel from './ClickableLabel.jsx';

export default class FunctionPropertyWidget extends Component {
    state = {
        variableName: '',
        variableLabel: '',
    };
    menu = null;

    componentDidMount() {
        if (this.props.initialData) {
            this.setProperty(this.props.initialData);
        }
    }

    setProperty(data) {
        this.setState({
            variableName: data.name || '',
            variableLabel: data.label || '',
        });
        if (this.props.onDirty) this.props.onDirty();
    }

    initMenu(data) {
        if (!data) return;
        const self = this;
        const onClick = function (menuItem) {
            self.setProperty({
                name: menuItem.name,
                label: menuItem.label,
                datatype: menuItem.datatype,
            });
        };
        const menuConfig = {menuItems: []};
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

    handleLabelClick = (e) => {
        if (this.menu) this.menu.show(e);
    };

    toXml() {
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
