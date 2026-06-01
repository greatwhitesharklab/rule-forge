import { Component } from 'react';

export interface MenuItemData {
    name: string;
    icon?: string | Record<string, string>;
    click?: (data: unknown, dispatch?: unknown) => void;
}

interface MenuItemProps {
    item: MenuItemData;
    data?: unknown;
    dispatch?: unknown;
}

export default class MenuItem extends Component<MenuItemProps> {
    render() {
        const { item, data, dispatch } = this.props;
        return (
            <li>
                <a href='###' onClick={(e) => { e.preventDefault(); item.click && item.click(data, dispatch); }}>
                    <i className={item.icon as string} style={{ color: 'var(--rf-primary)' }}></i> {item.name}
                </a>
            </li>
        );
    }
}
