import React, {Component, createRef} from 'react';
import ClickableLabel from './ClickableLabel.jsx';

export default class ConstantValueWidget extends Component {
    state = {
        category: '',
        constantName: '',
        constantLabel: '',
    };
    labelRef = createRef();
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
            category: data.constantCategory || '',
            constantName: data.constantName || '',
            constantLabel: data.constantLabel || '',
        });
        if (this.props.onDirty) this.props.onDirty();
    }

    initMenu(libraries) {
        const data = libraries || this.props.libraries;
        if (!data) return;
        const self = this;
        const onClick = function (menuItem) {
            self.setValue({
                constantCategory: menuItem.parent.parent.label,
                constantLabel: menuItem.label,
                constantName: menuItem.name,
            });
        };
        const config = {menuItems: []};
        data.forEach(function (item) {
            const categories = item.categories || [];
            categories.forEach(function (category) {
                const menuItem = {label: category.label};
                const constants = category.constants || [];
                constants.forEach(function (constant) {
                    if (!menuItem.subMenu) {
                        menuItem.subMenu = {menuItems: []};
                    }
                    menuItem.subMenu.menuItems.push({
                        name: constant.name,
                        label: constant.label,
                        onClick: onClick,
                    });
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

    getDisplayLabel() {
        const {category, constantLabel} = this.state;
        if (category && constantLabel) return category + '.' + constantLabel;
        return null;
    }

    toXml() {
        const {category, constantName, constantLabel} = this.state;
        if (!category) throw '常量不能为空！';
        return 'const-category="' + category + '" const="' + constantName + '" const-label="' + constantLabel + '"';
    }

    getDisplayContainer() {
        const {category, constantLabel} = this.state;
        return category + '.' + constantLabel;
    }

    render() {
        const label = this.getDisplayLabel();
        return (
            <ClickableLabel
                text={label || '请选择常量'}
                color={label ? '#0174DF' : undefined}
                onClick={this.handleLabelClick}
            />
        );
    }
}
