import { Component } from 'react';
import MenuItem from './MenuItem.tsx';
import type { MenuItemData } from './MenuItem.tsx';

interface MenuProps {
    items: MenuItemData[];
    data?: unknown;
    dispatch?: unknown;
    menuId?: string;
    visible?: boolean;
    x?: number | string;
    y?: number | string;
}

export default class Menu extends Component<MenuProps> {
    render() {
        const { items, data, dispatch, menuId, visible, x, y } = this.props;
        const result: React.ReactNode[] = [];
        items.forEach((item, index) => {
            result.push(
                <MenuItem item={item} key={item.name || index} data={data} dispatch={dispatch} />
            );
        });
        const menuStyle: React.CSSProperties = {
            color: 'var(--rf-text-primary)',
            display: visible ? 'block' : 'none',
            position: 'fixed',
            left: x || 0,
            top: y || 0
        };
        return (
            <div id={menuId} style={{ position: 'absolute', display: visible ? 'block' : 'none' }}>
                <ul className="dropdown-menu" style={menuStyle}>{result}</ul>
            </div>
        );
    }
}
