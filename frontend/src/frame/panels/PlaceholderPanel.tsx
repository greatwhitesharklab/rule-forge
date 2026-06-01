import {Component} from 'react';

interface PlaceholderInfo {
    title: string;
    desc: string;
    items: string[];
}

const DESCRIPTIONS: Record<string, PlaceholderInfo> = {
    datasource: {title: '数据源管理', desc: '管理 REST API、JDBC、消息队列等外部数据源，配置变量映射', items: ['数据源列表', '变量映射', '连接测试']},
    release: {title: '版本与发布', desc: '规则变更审批、环境隔离、灰度发布与一键回滚', items: ['变更记录', '审批流程', '环境管理', '发布历史']},
    ai: {title: '智能分析', desc: 'AI 分析决策日志，检测异常模式，生成规则优化建议', items: ['决策日志分析', '异常检测', '规则覆盖率', '优化建议']},
    settings: {title: '系统设置', desc: '权限配置、用户管理等系统级设置', items: ['权限配置', '用户管理']},
};

interface PlaceholderPanelProps {
    panelId: string;
}

export default class PlaceholderPanel extends Component<PlaceholderPanelProps> {
    render() {
        const {panelId} = this.props;
        const info = DESCRIPTIONS[panelId] || {title: panelId, desc: '', items: []};
        return (
            <div className="placeholder-panel" style={{height: '100%'}}>
                <div className="side-panel-header">{info.title}</div>
                <div className="placeholder-content">
                    <div className="placeholder-badge">即将推出</div>
                    <p className="placeholder-desc">{info.desc}</p>
                    <ul className="placeholder-items">
                        {info.items.map((item, i) => (
                            <li key={i}><i className="glyphicon glyphicon-chevron-right" style={{fontSize: 10, marginRight: 6, color: '#bbb'}}/>{item}</li>
                        ))}
                    </ul>
                </div>
            </div>
        );
    }
}
