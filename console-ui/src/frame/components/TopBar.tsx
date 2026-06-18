import {Component} from 'react';
import {connect} from 'react-redux';
import {formPost} from '@/api/client.js';
import AlertBell from '@/frame/AlertBell';
import {LogoutOutlined, SearchOutlined} from '@ant-design/icons';
import {CurrentUserContext} from '@/router/RequireAuth';
import * as ACTIONS from '@/frame/action.js';
import {selectProjectName} from '@/frame/reducer.ts';

interface TopBarProps {
    dispatch?: (action: unknown) => void;
    // connect 注入(frame store)
    activePanel?: string;
    projectName?: string | null;
}

interface TopBarState {
    userDropdownOpen: boolean;
}

interface FrameUiState {
    ui?: {activePanel?: string};
}

class TopBar extends Component<TopBarProps, TopBarState> {
    static contextType = CurrentUserContext;
    declare context: React.ContextType<typeof CurrentUserContext>;

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
            // V5.74.6:SPA 模式 — 跳根路径,index.html 加载后 BrowserRouter 把 `/` 重定向到 `/login`
            window.location.href = '/';
        });
    }

    /**
     * V5.101:顶栏文件搜索(从侧栏 FileTreePanel 迁入)。回车触发:写 searchFileName +
     * loadData(沿用 FileTreePanel 语义:classify=true、types=null、当前 projectName)。
     */
    _handleSearch = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key !== 'Enter') return;
        const value = (e.target as HTMLInputElement).value;
        const projectName = this.props.projectName ?? null;
        this.props.dispatch!(ACTIONS.setSearchFileName(value));
        this.props.dispatch!(ACTIONS.loadData(true, projectName, null, value));
    };

    render() {
        const {userDropdownOpen} = this.state;
        const {activePanel} = this.props;
        const currentUser = this.context as UserInfo | null;
        const username = (currentUser && currentUser.username) || 'admin';

        return (
            <div className="topbar">
                <div className="topbar-brand">
                    <span className="topbar-brand-logo">R</span>
                    <span className="topbar-brand-name">RuleForge</span>
                </div>

                {/* V5.101:文件搜索进顶栏 — 仅在规则编辑面板显示(文件树只在这里) */}
                {activePanel === 'rules' && (
                    <>
                        <div className="topbar-divider"/>
                        <div className="topbar-search">
                            <SearchOutlined/>
                            <input type="text"
                                   className="topbar-search-input fileSearchText"
                                   placeholder="搜索文件…"
                                   onKeyDown={this._handleSearch}/>
                        </div>
                    </>
                )}

                <div className="topbar-spacer"/>

                <div className="topbar-right">
                    {/* V5.45.5 — Frame 顶部 AlertBell:待审 draft 通知(V5.101:🔔 emoji → BellOutlined) */}
                    <AlertBell username={username}/>
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
                                    // 原 iframe editor.html?type=permission → SPA 化为新标签 /app/editor/permission
                                    window.open('/app/editor/permission', '_blank');
                                }}>
                                    <i className="rf rf-authority" style={{width: 16, fontSize: 12}}/>
                                    权限配置
                                </div>
                                <div className="topbar-dropdown-item" onClick={this._handleLogout.bind(this)}>
                                    <LogoutOutlined style={{width: 16, fontSize: 12}} />
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

// connect 到 frame store:读 activePanel / projectName(搜索 gated + loadData 用)
export default connect((state: FrameUiState) => ({
    activePanel: (state.ui && state.ui.activePanel) || 'rules',
    projectName: selectProjectName(state as any),
}))(TopBar);
