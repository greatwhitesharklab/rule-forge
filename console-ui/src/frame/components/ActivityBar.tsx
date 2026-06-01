import {Component} from 'react';
import {connect} from 'react-redux';
import {setActivePanel} from '@/frame/action.js';

interface PanelItem {
    id: string;
    icon: string;
    title: string;
}

interface ActivityBarProps {
    activePanel: string;
    dispatch: (action: unknown) => void;
}

const PANELS: PanelItem[] = [
    {id: 'rules', icon: 'glyphicon glyphicon-folder-open', title: '规则编辑'},
    {id: 'monitoring', icon: 'glyphicon glyphicon-signal', title: '监控告警'},
    {id: 'datasource', icon: 'glyphicon glyphicon-cloud', title: '数据源'},
    {id: 'release', icon: 'glyphicon glyphicon-tag', title: '版本发布'},
    {id: 'simulation', icon: 'glyphicon glyphicon-play-circle', title: '规则仿真'},
    {id: 'ai', icon: 'glyphicon glyphicon-education', title: '智能分析'},
];

const BOTTOM_PANELS: PanelItem[] = [
    {id: 'settings', icon: 'glyphicon glyphicon-cog', title: '系统设置'},
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
                <i className={item.icon}/>
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
