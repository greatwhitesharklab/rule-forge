import React, {Component} from 'react';

const OPERATORS = {
    GreaterThen: {label: '大于'},
    GreaterThenEquals: {label: '大于或等于'},
    LessThen: {label: '小于'},
    LessThenEquals: {label: '小于或等于'},
    Equals: {label: '等于'},
    EqualsIgnoreCase: {label: '等于(不分大小写)'},
    StartWith: {label: '开始于'},
    NotStartWith: {label: '不开始于'},
    EndWith: {label: '结束于'},
    NotEndWith: {label: '不结束于'},
    NotEquals: {label: '不等于'},
    NotEqualsIgnoreCase: {label: '不等于(不分大小写)'},
    In: {label: '在集合'},
    NotIn: {label: '不在集合'},
    Null: {label: '为空'},
    NotNull: {label: '不为空'},
    Match: {label: '匹配正则表达式'},
    NotMatch: {label: '不匹配正则表达式'},
    Contain: {label: '包含'},
    NotContain: {label: '不包含'},
};

export {OPERATORS};

export default class ComparisonOperatorWidget extends Component {
    state = {
        displayText: '请选择比较操作符',
        hasOperator: false,
    };
    menu = null;

    componentDidMount() {
        var self = this;
        var menuItems = Object.keys(OPERATORS).map(function (name) {
            return {
                label: OPERATORS[name].label,
                name: name,
                onClick: function (menuItem) {
                    if (self.props.onSelect) {
                        self.props.onSelect(menuItem.name);
                    }
                },
            };
        });
        self.menu = new RuleForge.menu.Menu({
            onHide: function () {
                if (self.props.onMenuHide) self.props.onMenuHide();
            },
            menuItems: menuItems,
        });
    }

    setOperator(operator) {
        var config = OPERATORS[operator];
        if (!config) return;
        this.setState({displayText: config.label, hasOperator: true});
    }

    handleLabelClick = (e) => {
        if (this.menu) this.menu.show(e);
    };

    render() {
        var displayText = this.state.displayText;
        var hasOperator = this.state.hasOperator;
        var style = {
            fontSize: 13,
            color: hasOperator ? 'red' : undefined,
            fontWeight: hasOperator ? 'bold' : undefined,
            cursor: 'pointer',
            height: 20,
            margin: 0,
            border: '1px dashed transparent',
            marginRight: 3,
        };
        return (
            <span style={style} onClick={this.handleLabelClick}>
                {displayText}
            </span>
        );
    }
}
