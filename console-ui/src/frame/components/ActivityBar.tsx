import {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import {setActivePanel} from '@/frame/action.js';
import {ApiOutlined, CloudOutlined, FolderOpenOutlined, HeartOutlined, PlayCircleOutlined, ProfileOutlined, ReadOutlined, SettingOutlined, TagOutlined, UserOutlined} from '@ant-design/icons';

interface PanelItem {
    id: string;
    icon: ReactNode;
    title: string;
}

interface ActivityBarProps {
    activePanel: string;
    dispatch: (action: unknown) => void;
}

const PANELS: PanelItem[] = [
    {id: 'rules', icon: <FolderOpenOutlined />, title: '规则编辑'},
    {id: 'monitoring', icon: <ApiOutlined />, title: '监控告警'},
    {id: 'datasource', icon: <CloudOutlined />, title: '数据源'},
    {id: 'release', icon: <TagOutlined />, title: '版本发布'},
    {id: 'simulation', icon: <PlayCircleOutlined />, title: '规则仿真'},
    {id: 'ai', icon: <ReadOutlined />, title: '智能分析'},
    {id: 'gitStatus', icon: <HeartOutlined />, title: 'Git 健康'},
    {id: 'userMgmt', icon: <UserOutlined />, title: '用户管理'},
    // V5.17: 用户/权限审计日志(admin 门控由后端控制,前端不重复)
    {id: 'auditLog', icon: <ProfileOutlined />, title: '审计日志'},
];

const BOTTOM_PANELS: PanelItem[] = [
    {id: 'settings', icon: <SettingOutlined />, title: '系统设置'},
];

class ActivityBar extends Component<ActivityBarProps> {
    handleClick(panelId: string) {
        this.props.dispatch(setActivePanel(panelId));
    }

    renderIcon(item: PanelItem, activePanel: string) {
        const active = activePanel === item.id;
        return (
            <div key={item.id}
                 className={'activity-bar-icon' + (active ? ' active' : '')}
                 title={item.title}
                 onClick={() => this.handleClick(item.id)}>
                {item.icon}
            </div>
        );
    }

    render() {
        const {activePanel} = this.props;
        return (
            <div className="activity-bar">
                {PANELS.map(item => this.renderIcon(item, activePanel))}
                <div style={{flex: 1}}/>
                {BOTTOM_PANELS.map(item => this.renderIcon(item, activePanel))}
            </div>
        );
    }
}

const selector = (state: { ui?: { activePanel?: string } }) => ({
    activePanel: (state.ui && state.ui.activePanel) || 'rules'
});
export default connect(selector)(ActivityBar);
