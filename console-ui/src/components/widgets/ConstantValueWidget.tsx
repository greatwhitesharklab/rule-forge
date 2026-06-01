import React, {Component, createRef} from 'react';
import ClickableLabel from './ClickableLabel';

interface ConstantValueWidgetProps {
    initialData?: {
        constantCategory?: string;
        constantName?: string;
        constantLabel?: string;
    };
    libraries?: ConstantLibrary[];
    onDirty?: () => void;
    ref?: React.Ref<any>;
}

interface ConstantValueWidgetState {
    category: string;
    constantName: string;
    constantLabel: string;
}

interface ConstantLibrary {
    categories?: ConstantCategory[];
}

interface ConstantCategory {
    label: string;
    constants?: { name: string; label: string }[];
}

interface ConstantValueData {
    constantCategory?: string;
    constantName?: string;
    constantLabel?: string;
}

export default class ConstantValueWidget extends Component<ConstantValueWidgetProps, ConstantValueWidgetState> {
    state = {
        category: '',
        constantName: '',
        constantLabel: '',
    };
    menu: MenuInstance | null = null;

    componentDidMount() {
        if (this.props.initialData) {
            this.initData(this.props.initialData);
        }
        this.initMenu();
    }

    componentDidUpdate(prevProps: ConstantValueWidgetProps) {
        if (this.props.libraries !== prevProps.libraries) {
            this.initMenu();
        }
    }

    initData(data: ConstantValueData) {
        if (!data) return;
        this.setValue(data);
    }

    setValue(data: ConstantValueData) {
        this.setState({
            category: data.constantCategory || '',
            constantName: data.constantName || '',
            constantLabel: data.constantLabel || '',
        });
        if (this.props.onDirty) this.props.onDirty();
    }

    initMenu(libraries?: ConstantLibrary[]) {
        const data = libraries || this.props.libraries;
        if (!data) return;
        const self = this;
        const onClick = function (menuItem: MenuItemConfig) {
            self.setValue({
                constantCategory: menuItem.parent!.parent!.label,
                constantLabel: menuItem.label,
                constantName: menuItem.name,
            });
        };
        const config: MenuConfig = {menuItems: []};
        data.forEach(function (item) {
            const categories = item.categories || [];
            categories.forEach(function (category) {
                const menuItem: MenuItemConfig = {label: category.label, name: ''};
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

    handleLabelClick = (e: React.MouseEvent<HTMLSpanElement>) => {
        if (this.menu) this.menu.show(e);
    };

    getDisplayLabel(): string | null {
        const {category, constantLabel} = this.state;
        if (category && constantLabel) return category + '.' + constantLabel;
        return null;
    }

    toXml(): string {
        const {category, constantName, constantLabel} = this.state;
        if (!category) throw '常量不能为空！';
        return 'const-category="' + category + '" const="' + constantName + '" const-label="' + constantLabel + '"';
    }

    getDisplayContainer(): string {
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
