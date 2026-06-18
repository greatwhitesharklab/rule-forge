import {Component} from 'react';
import {Menu as AntMenu} from 'antd';
import type {MenuItemData} from './MenuItem.tsx';

interface MenuProps {
    items: MenuItemData[];
    data?: unknown;
    dispatch?: unknown;
    menuId?: string;
    visible?: boolean;
    x?: number | string;
    y?: number | string;
}

/**
 * V5.101.2:右键上下文菜单。原 `<ul className="rf-dropdown-menu">` + 自定义 MenuItem →
 * antd Menu(items)。保留手动 x/y 定位(右键菜单位置由调用方算)。click(data, dispatch) 不变。
 */
export default class Menu extends Component<MenuProps> {
    render() {
        const {items, data, dispatch, menuId, visible, x, y} = this.props;
        const antItems = items.map((item, index) => ({
            key: item.name || String(index),
            label: item.name,
            icon: typeof item.icon === 'string' ? <i className={item.icon as string}/> : null,
            onClick: () => item.click && item.click(data, dispatch),
        }));
        return (
            <div id={menuId} style={{
                position: 'fixed', left: x || 0, top: y || 0,
                display: visible ? 'block' : 'none', zIndex: 1050,
                background: 'var(--rf-bg-container)', borderRadius: 'var(--rf-radius-base)',
                border: '1px solid var(--rf-border-split)', boxShadow: 'var(--rf-shadow-popup)',
                overflow: 'hidden', minWidth: 140
            }}>
                <AntMenu items={antItems} style={{borderInlineEnd: 'none'}}/>
            </div>
        );
    }
}
