import {Component} from 'react';
import {connect} from 'react-redux';
import * as action from './action';

class ReleasePanel extends Component {
    constructor(props) {
        super(props);
        this.state = {projectName: ''};
    }

    componentDidMount() {
        this.loadProjectData();
    }

    componentDidUpdate(prevProps) {
        const prevProject = prevProps.projectName;
        const curProject = this.getProjectName();
        if (curProject && curProject !== prevProject && curProject !== this.state.projectName) {
            this.loadProjectData();
        }
    }

    loadProjectData() {
        const projectName = this.getProjectName();
        if (projectName) {
            this.setState({projectName});
            this.props.dispatch(action.loadEnvironments(projectName));
        }
    }

    getProjectName() {
        if (window._projectName) return window._projectName;
        const hash = window.location.hash || '';
        const match = hash.match(/project=([^&]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    handleTabChange(tab) {
        this.props.dispatch(action.setTab(tab));
        const {projectName} = this.state;
        if (projectName) {
            if (tab === 'approvals') {
                this.props.dispatch(action.loadApprovals(projectName));
            } else if (tab === 'history') {
                this.props.dispatch(action.loadDeploymentHistory(projectName));
            } else if (tab === 'environments') {
                this.props.dispatch(action.loadEnvironments(projectName));
            }
        }
    }

    render() {
        const {activeTab, environments, approvals, deploymentHistory, environmentsLoading} = this.props;
        const {projectName} = this.state;

        const tabs = [
            {id: 'environments', label: '环境管理', icon: 'glyphicon glyphicon-globe'},
            {id: 'approvals', label: '审批流程', icon: 'glyphicon glyphicon-check'},
            {id: 'history', label: '部署历史', icon: 'glyphicon glyphicon-time'},
        ];

        return (
            <div style={{height: '100%', display: 'flex', flexDirection: 'column', background: '#fff'}}>
                {/* Header */}
                <div style={{padding: '10px 15px', borderBottom: '1px solid #e0e0e0', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                    <h5 style={{margin: 0, fontWeight: 600}}>版本发布</h5>
                    <span style={{fontSize: 12, color: '#999'}}>{projectName}</span>
                </div>

                {/* Tab Bar */}
                <div style={{display: 'flex', borderBottom: '1px solid #e0e0e0', background: '#fafafa'}}>
                    {tabs.map(tab => (
                        <button key={tab.id}
                                onClick={() => this.handleTabChange(tab.id)}
                                style={{
                                    flex: 1, padding: '8px 0', border: 'none', cursor: 'pointer',
                                    background: activeTab === tab.id ? '#fff' : '#fafafa',
                                    borderBottom: activeTab === tab.id ? '2px solid #5470c6' : '2px solid transparent',
                                    fontWeight: activeTab === tab.id ? 600 : 400,
                                    fontSize: 13
                                }}>
                            <span className={tab.icon} style={{marginRight: 4}}/>
                            {tab.label}
                        </button>
                    ))}
                </div>

                {/* Tab Content */}
                <div style={{flex: 1, overflow: 'auto', padding: 15}}>
                    {!projectName ? (
                        <div style={{textAlign: 'center', padding: 40, color: '#999'}}>
                            <span className="glyphicon glyphicon-info-sign" style={{fontSize: 24, display: 'block', marginBottom: 10}}/>
                            请先选择一个项目
                        </div>
                    ) : activeTab === 'environments' ? (
                        this.renderEnvironments(environments, environmentsLoading)
                    ) : activeTab === 'approvals' ? (
                        this.renderApprovals(approvals)
                    ) : (
                        this.renderHistory(deploymentHistory)
                    )}
                </div>
            </div>
        );
    }

    renderEnvironments(environments, loading) {
        if (loading) return <div style={{textAlign: 'center', padding: 20}}>加载中...</div>;
        if (!environments || environments.length === 0) {
            return <div style={{textAlign: 'center', padding: 40, color: '#999'}}>暂无环境配置</div>;
        }

        const envLabels = {test: '测试环境', prod: '生产环境', uat: 'UAT环境', dev: '开发环境'};

        return (
            <div>
                {environments.map((env, idx) => (
                    <div key={idx} style={{
                        border: '1px solid #e0e0e0', borderRadius: 4, padding: 15, marginBottom: 10,
                        background: '#fafafa'
                    }}>
                        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                            <div>
                                <span style={{
                                    display: 'inline-block', padding: '2px 8px', borderRadius: 3, fontSize: 12,
                                    background: env.execEnv === 'prod' ? '#e8f5e9' : env.execEnv === 'test' ? '#e3f2fd' : '#f5f5f5',
                                    color: env.execEnv === 'prod' ? '#2e7d32' : env.execEnv === 'test' ? '#1565c0' : '#666'
                                }}>
                                    {envLabels[env.execEnv] || env.execEnv}
                                </span>
                                <span style={{marginLeft: 10, fontSize: 14, fontWeight: 500}}>
                                    {env.projectVersion || '-'}
                                </span>
                            </div>
                            {env.execEnv === 'prod' && env.projectVersion && (
                                <button className="btn btn-default btn-xs"
                                        onClick={() => {
                                            window.bootbox.confirm('确认回滚到上一版本？', (ok) => {
                                                if (ok) {
                                                    this.props.dispatch(action.rollbackVersion(
                                                        this.state.projectName, env.packageId, env.projectVersion, 'prod'));
                                                }
                                            });
                                        }}>
                                    回滚
                                </button>
                            )}
                        </div>
                        {env.packageId && (
                            <div style={{fontSize: 12, color: '#999', marginTop: 5}}>
                                包: {env.packageId} | 更新: {env.updateTime || '-'}
                            </div>
                        )}
                    </div>
                ))}
            </div>
        );
    }

    renderApprovals(approvals) {
        if (!approvals || approvals.length === 0) {
            return <div style={{textAlign: 'center', padding: 40, color: '#999'}}>暂无审批记录</div>;
        }

        const statusLabels = {pending: '待审批', approved: '已通过', rejected: '已驳回'};
        const statusColors = {pending: '#f57c00', approved: '#2e7d32', rejected: '#c62828'};

        return (
            <div>
                {approvals.map((task, idx) => (
                    <div key={task.id || idx} style={{
                        border: '1px solid #e0e0e0', borderRadius: 4, padding: 12, marginBottom: 8
                    }}>
                        <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                            <div>
                                <span style={{
                                    display: 'inline-block', padding: '2px 6px', borderRadius: 3, fontSize: 11,
                                    background: statusColors[task.status] === '#2e7d32' ? '#e8f5e9' :
                                                statusColors[task.status] === '#f57c00' ? '#fff3e0' : '#ffebee',
                                    color: statusColors[task.status] || '#666'
                                }}>
                                    {statusLabels[task.status] || task.status}
                                </span>
                                <span style={{marginLeft: 8, fontWeight: 500}}>{task.title || task.projectVersion}</span>
                            </div>
                            {task.status === 'pending' && (
                                <div>
                                    <button className="btn btn-success btn-xs" style={{marginRight: 4}}
                                            onClick={() => {
                                                window.bootbox.confirm('确认通过该审批？', (ok) => {
                                                    if (ok) this.props.dispatch(action.approveTask(task.id, ''));
                                                });
                                            }}>通过</button>
                                    <button className="btn btn-danger btn-xs"
                                            onClick={() => {
                                                window.bootbox.prompt({title: '驳回原因', callback: (remark) => {
                                                    if (remark !== null) this.props.dispatch(action.rejectTask(task.id, remark));
                                                }});
                                            }}>驳回</button>
                                </div>
                            )}
                        </div>
                        <div style={{fontSize: 12, color: '#999', marginTop: 5}}>
                            版本: {task.projectVersion} | 环境: {task.execEnv} | 申请人: {task.requester} | {task.createTime || ''}
                        </div>
                    </div>
                ))}
            </div>
        );
    }

    renderHistory(history) {
        if (!history || history.length === 0) {
            return <div style={{textAlign: 'center', padding: 40, color: '#999'}}>暂无部署历史</div>;
        }

        const statusLabels = {deployed: '已部署', failed: '失败', rolled_back: '已回滚', superseded: '已替换'};
        const statusColors = {deployed: '#2e7d32', failed: '#c62828', rolled_back: '#f57c00', superseded: '#999'};

        return (
            <table className="table table-condensed" style={{fontSize: 13}}>
                <thead>
                    <tr>
                        <th>版本</th>
                        <th>环境</th>
                        <th>状态</th>
                        <th>部署人</th>
                        <th>时间</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    {history.map((dep, idx) => (
                        <tr key={dep.id || idx}>
                            <td>{dep.projectVersion}</td>
                            <td>{dep.execEnv}</td>
                            <td style={{color: statusColors[dep.deployStatus] || '#666'}}>
                                {statusLabels[dep.deployStatus] || dep.deployStatus}
                            </td>
                            <td>{dep.deployUser || '-'}</td>
                            <td style={{fontSize: 11}}>{dep.deployTime || '-'}</td>
                            <td>
                                {dep.deployStatus === 'deployed' && dep.execEnv === 'prod' && idx > 0 && (
                                    <button className="btn btn-warning btn-xs"
                                            onClick={() => {
                                                window.bootbox.confirm('确认回滚到版本 ' + dep.projectVersion + '？', (ok) => {
                                                    if (ok) {
                                                        this.props.dispatch(action.rollbackVersion(
                                                            this.state.projectName, dep.packageId,
                                                            dep.projectVersion, dep.execEnv));
                                                    }
                                                });
                                            }}>回滚到此版本</button>
                                )}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        );
    }
}

export default connect(state => ({...state.release}))(ReleasePanel);
