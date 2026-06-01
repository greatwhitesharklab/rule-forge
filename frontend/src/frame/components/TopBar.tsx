import {Component} from 'react';
import * as componentEvent from '@/components/componentEvent.js';
import {formPost} from '@/api/client.js';

interface TopBarProps {
    dispatch?: (action: unknown) => void;
}

interface TopBarState {
    userDropdownOpen: boolean;
}

export default class TopBar extends Component<TopBarProps, TopBarState> {
    constructor(props: TopBarProps) {
        super(props);
        this.state = {
            userDropdownOpen: false
        };
        this._handleClickOutside = this._handleClickOutside.bind(this);
    }

    componentDidMount() {
        document.addEventListener('click', this._handleClickOutside as EventListener);
    }

    componentWillUnmount() {
        document.removeEventListener('click', this._handleClickOutside as EventListener);
    }

    _handleClickOutside(e: MouseEvent) {
        if (this.state.userDropdownOpen) {
            if (!(e.target as Element).closest('.topbar-user')) {
                this.setState({userDropdownOpen: false});
            }
        }
    }

    _handleLogout(e: React.MouseEvent) {
        e.preventDefault();
        formPost('/frame/logout', {}).then(function () {
            window.location.href = 'html/login.html';
        });
    }

    render() {
        const {userDropdownOpen} = this.state;
        const username = (window.__currentUser && window.__currentUser.username) || 'admin';

        return (
            <div className="topbar">
                <div className="topbar-brand">
                    <span className="topbar-brand-logo">R</span>
                    <span className="topbar-brand-name">RuleForge</span>
                </div>
                <div style={{flex: 1}}/>
                <div className="topbar-right">
                    <div className="topbar-user">
                        <button className="topbar-user-btn" onClick={(e) => {
                            e.stopPropagation();
                            this.setState({userDropdownOpen: !userDropdownOpen});
                        }}>
                            <div className="topbar-user-avatar">{username.charAt(0).toUpperCase()}</div>
                        </button>
                        {userDropdownOpen && (
                            <div className="topbar-dropdown topbar-user-dropdown">
                                <div className="topbar-dropdown-info">
                                    <div className="topbar-user-avatar" style={{width: 32, height: 32, fontSize: 14}}>
                                        {username.charAt(0).toUpperCase()}
                                    </div>
                                    <span>{username}</span>
                                </div>
                                <div className="topbar-dropdown-divider"/>
                                <div className="topbar-dropdown-item" onClick={() => {
                                    this.setState({userDropdownOpen: false});
                                    componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK, {
                                        id: 'security_config_',
                                        name: '资源权限配置',
                                        fullPath: 'security_config_',
                                        path: './html/editor.html?type=permission'
                                    });
                                }}>
                                    <i className="rf rf-authority" style={{width: 16, fontSize: 12}}/>
                                    权限配置
                                </div>
                                <div className="topbar-dropdown-item" onClick={this._handleLogout.bind(this)}>
                                    <i className="glyphicon glyphicon-log-out" style={{width: 16, fontSize: 12}}/>
                                    退出登录
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        );
    }
}
