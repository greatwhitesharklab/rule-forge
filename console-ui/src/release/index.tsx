import {Component, ReactNode} from 'react';
import {connect} from 'react-redux';
import * as action from './action';
import type {EnvironmentInfo, ApprovalTask, DeploymentRecord, ExecutorNode, GrayStrategy, ShadowConfig, ShadowComparison, ShadowStats} from './action';
import type {ReleaseState} from './reducer';

import {alert, confirm, prompt} from '@/utils/modal';
import {Button, Select, Table, Tabs, Tag} from 'antd';
import type {ColumnsType} from 'antd/es/table';
import PageShell from '@/frame/components/PageShell';
import {CheckOutlined, ClockCircleOutlined, CopyOutlined, GlobalOutlined, HddOutlined, InfoCircleOutlined, PlusOutlined, RetweetOutlined, UploadOutlined} from '@ant-design/icons';
interface ReleasePanelState {
    projectName: string;
}

interface ReleasePanelProps {
    dispatch: (action: any) => any;
    projectName?: string;
    activeTab: string;
    environments: EnvironmentInfo[];
    environmentsLoading: boolean;
    approvals: ApprovalTask[];
    approvalsLoading: boolean;
    deploymentHistory: DeploymentRecord[];
    historyLoading: boolean;
    nodes: ExecutorNode[];
    nodesLoading: boolean;
    grayStrategies: GrayStrategy[];
    grayStrategiesLoading: boolean;
    shadowConfigs: ShadowConfig[];
    shadowConfigsLoading: boolean;
    shadowComparisons: ShadowComparison[];
    shadowComparisonsLoading: boolean;
    shadowStats: ShadowStats | null;
    shadowStatsLoading: boolean;
}

interface TabDef {
    id: string;
    label: string;
    icon: ReactNode;
}

class ReleasePanel extends Component<ReleasePanelProps, ReleasePanelState> {
    constructor(props: ReleasePanelProps) {
        super(props);
        this.state = {projectName: ''};
    }

    componentDidMount() {
        this.loadProjectData();
    }

    componentDidUpdate(prevProps: ReleasePanelProps) {
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

    getProjectName(): string {
        // 优先用 frame store 注入的 projectName(connect selector),fallback 到 URL hash
        if (this.props.projectName) return this.props.projectName;
        const hash = window.location.hash || '';
        const match = hash.match(/project=([^&]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    handleTabChange(tab: string) {
        this.props.dispatch(action.setTab(tab));
        const {projectName} = this.state;
        if (projectName) {
            if (tab === 'approvals') {
                this.props.dispatch(action.loadApprovals(projectName));
            } else if (tab === 'history') {
                this.props.dispatch(action.loadDeploymentHistory(projectName));
            } else if (tab === 'environments') {
                this.props.dispatch(action.loadEnvironments(projectName));
            } else if (tab === 'nodes') {
                this.props.dispatch(action.loadNodes());
            } else if (tab === 'gray') {
                this.props.dispatch(action.loadGrayStrategies());
            } else if (tab === 'shadow') {
                this.props.dispatch(action.loadShadowConfigs());
            }
        }
    }

    render() {
        const {activeTab, environments, approvals, deploymentHistory, environmentsLoading, grayStrategies, grayStrategiesLoading, shadowConfigs, shadowConfigsLoading, shadowComparisons, shadowComparisonsLoading, shadowStats} = this.props;
        const {projectName} = this.state;

        const tabs: TabDef[] = [
            {id: 'environments', label: '环境管理', icon: <GlobalOutlined />,},
            {id: 'approvals', label: '审批流程', icon: <CheckOutlined />,},
            {id: 'history', label: '部署历史', icon: <ClockCircleOutlined />,},
            {id: 'nodes', label: '节点管理', icon: <HddOutlined />,},
            {id: 'gray', label: '灰度策略', icon: <RetweetOutlined />,},
            {id: 'shadow', label: '陪跑配置', icon: <CopyOutlined />,},
        ];

        return (
            <PageShell
                title="版本发布"
                description={projectName ? `项目:${projectName}` : '请先在左侧选择一个项目'}
                toolbar={
                    <Tabs
                        activeKey={activeTab}
                        onChange={(key: string) => this.handleTabChange(key)}
                        items={tabs.map(t => ({key: t.id, label: (<><span style={{marginRight: 6}}>{t.icon}</span>{t.label}</>)}))}
                    />
                }
            >
                {!projectName ? (
                    <div style={{textAlign: 'center', padding: 40, color: 'var(--rf-text-tertiary)'}}>
                        <InfoCircleOutlined style={{fontSize: 24, display: 'block', marginBottom: 10}} />
                        请先选择一个项目
                    </div>
                ) : activeTab === 'environments' ? (
                    this.renderEnvironments(environments, environmentsLoading)
                ) : activeTab === 'approvals' ? (
                    this.renderApprovals(approvals)
                ) : activeTab === 'nodes' ? (
                    this.renderNodes(this.props.nodes)
                ) : activeTab === 'gray' ? (
                    this.renderGrayStrategies(grayStrategies, grayStrategiesLoading)
                ) : activeTab === 'shadow' ? (
                    this.renderShadowPanel(shadowConfigs, shadowConfigsLoading, shadowComparisons, shadowComparisonsLoading, shadowStats)
                ) : (
                    this.renderHistory(deploymentHistory)
                )}
            </PageShell>
        );
    }

    renderEnvironments(environments: EnvironmentInfo[], loading: boolean) {
        if (loading) return <div style={{textAlign: 'center', padding: 20}}>加载中...</div>;
        if (!environments || environments.length === 0) {
            return <div style={{textAlign: 'center', padding: 40, color: '#999'}}>暂无环境配置</div>;
        }

        const envLabels: Record<string, string> = {test: '测试环境', prod: '生产环境', uat: 'UAT环境', dev: '开发环境'};

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
                                <Button size="small"
                                        onClick={() => {
                                            confirm('确认回滚到上一版本？', (ok) => {
                                                if (ok) {
                                                    this.props.dispatch(action.rollbackVersion(
                                                        this.state.projectName, env.packageId, env.projectVersion, 'prod'));
                                                }
                                            });
                                        }}>
                                    回滚
                                </Button>
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

    renderApprovals(approvals: ApprovalTask[]) {
        if (!approvals || approvals.length === 0) {
            return <div style={{textAlign: 'center', padding: 40, color: '#999'}}>暂无审批记录</div>;
        }

        const statusLabels: Record<string, string> = {pending: '待审批', approved: '已通过', rejected: '已驳回'};
        const statusColors: Record<string, string> = {pending: '#f57c00', approved: '#2e7d32', rejected: '#c62828'};

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
                                    <Button color="green" size="small" style={{marginRight: 4}}
                                            onClick={() => {
                                                confirm('确认通过该审批？', (ok) => {
                                                    if (ok) this.props.dispatch(action.approveTask(task.id, ''));
                                                });
                                            }}>通过</Button>
                                    <Button color="danger" size="small"
                                            onClick={() => {
                                                prompt('驳回原因', (remark: string | null) => {
                                                    if (remark !== null) this.props.dispatch(action.rejectTask(task.id, remark));
                                                });
                                            }}>驳回</Button>
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

    renderHistory(history: DeploymentRecord[]) {
        if (!history || history.length === 0) {
            return <div style={{textAlign: 'center', padding: 40, color: 'var(--rf-text-tertiary)'}}>暂无部署历史</div>;
        }

        const statusLabels: Record<string, string> = {deployed: '已部署', failed: '失败', rolled_back: '已回滚', superseded: '已替换'};
        const statusColors: Record<string, string> = {deployed: 'success', failed: 'error', rolled_back: 'warning', superseded: 'default'};

        const columns: ColumnsType<DeploymentRecord> = [
            {title: '版本', dataIndex: 'projectVersion', key: 'version'},
            {title: '环境', dataIndex: 'execEnv', key: 'env'},
            {title: '状态', dataIndex: 'deployStatus', key: 'status',
                render: (s: string) => <Tag color={statusColors[s] || 'default'}>{statusLabels[s] || s}</Tag>},
            {title: '部署人', dataIndex: 'deployUser', key: 'user', render: (v: string) => v || '-'},
            {title: '时间', dataIndex: 'deployTime', key: 'time', render: (v: string) => v || '-', width: 160},
            {
                title: '操作', key: 'actions', width: 140,
                render: (_: unknown, dep: DeploymentRecord, idx: number) => (
                    dep.deployStatus === 'deployed' && dep.execEnv === 'prod' && idx > 0 ? (
                        <Button size="small" danger onClick={() => {
                            confirm('确认回滚到版本 ' + dep.projectVersion + '？', (ok) => {
                                if (ok) {
                                    this.props.dispatch(action.rollbackVersion(
                                        this.state.projectName, dep.packageId,
                                        dep.projectVersion, dep.execEnv));
                                }
                            });
                        }}>回滚到此版本</Button>
                    ) : null
                )
            },
        ];

        return (
            <Table rowKey="id"
                   columns={columns} dataSource={history} pagination={false} size="small" />
        );
    }

    renderNodes(nodes: ExecutorNode[]) {
        if (this.props.nodesLoading) return <div style={{textAlign: 'center', padding: 20}}>加载中...</div>;
        if (!nodes || nodes.length === 0) {
            return (
                <div style={{textAlign: 'center', padding: 40, color: '#999'}}>
                    <p>暂无注册的执行器节点</p>
                    <p style={{fontSize: 12}}>执行器启动后会自动注册</p>
                </div>
            );
        }


        return (
            <div>
                {/* Node list */}
                <Table<ExecutorNode> rowKey="id" dataSource={nodes} pagination={false} size="small"
                    columns={[
                        {title: '节点名称', dataIndex: 'nodeName', key: 'name'},
                        {title: 'URL', dataIndex: 'nodeUrl', key: 'url', ellipsis: true,
                            render: (v: string) => <span style={{fontSize: 11}}>{v}</span>},
                        {title: '环境', dataIndex: 'execEnv', key: 'env', render: (v: string) => v || '-'},
                        {
                            title: '分组', key: 'group', width: 110,
                            render: (_: unknown, node: ExecutorNode) => (
                                <Select size="small" value={node.nodeGroup || 'default'}
                                        onChange={(v: string) => this.props.dispatch(action.updateNodeGroup(node.id, v))}
                                        options={[{value: 'default', label: '默认'}, {value: 'canary', label: '灰度'}, {value: 'vip', label: 'VIP'}]}
                                        style={{width: '100%'}}/>
                            )
                        },
                        {
                            title: '状态', dataIndex: 'status', key: 'status', width: 80,
                            render: (s: string) => <Tag color={s === 'active' ? 'success' : 'error'}>{s === 'active' ? '在线' : '离线'}</Tag>
                        },
                        {title: '心跳', dataIndex: 'lastHeartbeat', key: 'heartbeat', render: (v: string) => v || '-', width: 160},
                    ]}/>

                {/* Canary deploy section */}
                <div style={{marginTop: 20, padding: 15, border: '1px solid #e0e0e0', borderRadius: 4, background: '#fafafa'}}>
                    <h6 style={{margin: '0 0 10px 0', fontWeight: 600}}>灰度部署</h6>
                    <div style={{display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap'}}>
                        <input id="canary-package" placeholder="包ID" style={{padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3, width: 120}}/>
                        <input id="canary-version" placeholder="版本号" style={{padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3, width: 100}}/>
                        <select id="canary-env" style={{padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}>
                            <option value="prod">生产环境</option>
                            <option value="test">测试环境</option>
                        </select>
                        <select id="canary-group" style={{padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}>
                            <option value="canary">灰度节点</option>
                            <option value="vip">VIP节点</option>
                            <option value="default">默认节点</option>
                        </select>
                        <Button color="gold" size="small" icon={<UploadOutlined/>} onClick={() => {
                            const packageId = (document.getElementById('canary-package') as HTMLInputElement).value;
                            const version = (document.getElementById('canary-version') as HTMLInputElement).value;
                            const execEnv = (document.getElementById('canary-env') as HTMLSelectElement).value;
                            const nodeGroup = (document.getElementById('canary-group') as HTMLSelectElement).value;
                            if (!packageId || !version) {
                                alert('请填写包ID和版本号');
                                return;
                            }
                            confirm(`确认将版本 ${version} 部署到 ${nodeGroup} 节点组？`, (ok) => {
                                if (ok) {
                                    this.props.dispatch(action.deployToGroup(
                                        this.state.projectName, packageId, version, execEnv, nodeGroup));
                                }
                            });
                        }}>
                            灰度部署
                        </Button>
                    </div>
                </div>
            </div>
        );
    }

    renderGrayStrategies(strategies: GrayStrategy[], loading: boolean) {
        if (loading) return <div style={{textAlign: 'center', padding: 20}}>加载中...</div>;

        const typeLabels: Record<string, string> = {PERCENT_USER: '用户比例', PERCENT_RANDOM: '随机比例', WHITELIST: '白名单'};

        return (
            <div>
                {/* Strategy list */}
                {(!strategies || strategies.length === 0) ? (
                    <div style={{textAlign: 'center', padding: 40, color: '#999'}}>
                        <p>暂无灰度策略</p>
                        <p style={{fontSize: 12}}>点击下方按钮创建灰度策略</p>
                    </div>
                ) : (
                    <Table<GrayStrategy> rowKey="id" dataSource={strategies} pagination={false} size="small"
                        columns={[
                            {title: '策略名称', dataIndex: 'strategyName', key: 'name'},
                            {title: '类型', dataIndex: 'strategyType', key: 'type',
                                render: (t: string) => <Tag color="blue">{typeLabels[t] || t}</Tag>},
                            {title: '包ID', dataIndex: 'packageId', key: 'pkg', render: (v: string) => <span style={{fontSize: 11}}>{v}</span>},
                            {title: '灰度版本', dataIndex: 'targetGitTag', key: 'target',
                                render: (v: string) => <span style={{fontSize: 11, color: 'var(--rf-warning)'}}>{v}</span>},
                            {title: '基准版本', dataIndex: 'baselineGitTag', key: 'baseline',
                                render: (v: string) => <span style={{fontSize: 11}}>{v}</span>},
                            {title: '状态', dataIndex: 'enabled', key: 'enabled',
                                render: (v: boolean) => <Tag color={v ? 'success' : 'error'}>{v ? '启用' : '停用'}</Tag>},
                            {title: '操作', key: 'actions',
                                render: (_: unknown, s: GrayStrategy) => (
                                    <>
                                        <Button size="small" style={{marginRight: 4}}
                                                onClick={() => this.props.dispatch(action.toggleGrayStrategy(s.id, !s.enabled, s.projectId, s.packageId))}>
                                            {s.enabled ? '停用' : '启用'}
                                        </Button>
                                        <Button size="small" danger
                                                onClick={() => this.props.dispatch(action.deleteGrayStrategy(s.id, s.projectId, s.packageId))}>
                                            删除
                                        </Button>
                                    </>
                                )},
                        ]}/>
                )}

                {/* Create form */}
                <div style={{marginTop: 15, padding: 15, border: '1px solid #e0e0e0', borderRadius: 4, background: '#fafafa'}}>
                    <h6 style={{margin: '0 0 10px 0', fontWeight: 600}}>新建灰度策略</h6>
                    <div style={{display: 'flex', flexDirection: 'column', gap: 8}}>
                        <div style={{display: 'flex', gap: 8}}>
                            <input id="gray-name" placeholder="策略名称" style={{flex: 1, padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}/>
                            <select id="gray-type" style={{padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}>
                                <option value="PERCENT_USER">用户比例</option>
                                <option value="PERCENT_RANDOM">随机比例</option>
                                <option value="WHITELIST">白名单</option>
                            </select>
                        </div>
                        <div style={{display: 'flex', gap: 8}}>
                            <input id="gray-package" placeholder="包ID" style={{flex: 1, padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}/>
                            <input id="gray-percent" placeholder="灰度百分比 (0-100)" type="number" min="0" max="100"
                                   style={{width: 160, padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}/>
                        </div>
                        <div>
                            <input id="gray-whitelist" placeholder="白名单用户ID (逗号分隔, 仅白名单类型)" style={{width: '100%', padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}/>
                        </div>
                        <div style={{display: 'flex', gap: 8}}>
                            <input id="gray-target" placeholder="目标版本 git tag" style={{flex: 1, padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}/>
                            <input id="gray-baseline" placeholder="基准版本 git tag" style={{flex: 1, padding: '4px 8px', fontSize: 13, border: '1px solid #ddd', borderRadius: 3}}/>
                        </div>
                        <Button type="primary" size="small" onClick={() => {
                            const name = (document.getElementById('gray-name') as HTMLInputElement).value;
                            const type = (document.getElementById('gray-type') as HTMLSelectElement).value;
                            const packageId = (document.getElementById('gray-package') as HTMLInputElement).value;
                            const percent = (document.getElementById('gray-percent') as HTMLInputElement).value;
                            const whitelist = (document.getElementById('gray-whitelist') as HTMLInputElement).value;
                            const target = (document.getElementById('gray-target') as HTMLInputElement).value;
                            const baseline = (document.getElementById('gray-baseline') as HTMLInputElement).value;
                            if (!name || !packageId || !target || !baseline) {
                                alert('请填写策略名称、包ID、目标版本和基准版本');
                                return;
                            }
                            this.props.dispatch(action.createGrayStrategy({
                                strategyName: name,
                                strategyType: type,
                                packageId,
                                projectId: '1',
                                grayPercent: percent ? parseInt(percent) : 0,
                                whitelist: whitelist || null,
                                targetGitTag: target,
                                baselineGitTag: baseline,
                                enabled: true
                            }));
                        }}>
                            <PlusOutlined style={{marginRight: 4}} />
                            创建策略
                        </Button>
                    </div>
                </div>
            </div>
        );
    }

    renderShadowPanel(configs: ShadowConfig[], configsLoading: boolean, comparisons: ShadowComparison[], comparisonsLoading: boolean, stats: ShadowStats | null) {
        return (
            <div>
                {this.renderShadowConfigs(configs, configsLoading)}
                <div style={{marginTop: 20}}>
                    {this.renderShadowComparisons(comparisons, comparisonsLoading, stats)}
                </div>
            </div>
        );
    }

    renderShadowConfigs(configs: ShadowConfig[], loading: boolean) {
        if (loading) return <div style={{textAlign: 'center', padding: 20}}>加载中...</div>;

        return (
            <div>
                <h6 style={{fontWeight: 600, marginBottom: 10}}>陪跑配置</h6>
                {(!configs || configs.length === 0) ? (
                    <div style={{textAlign: 'center', padding: 20, color: '#999'}}>暂无陪跑配置</div>
                ) : (
                    <Table<ShadowConfig> rowKey="id" dataSource={configs} pagination={false} size="small"
                        columns={[
                            {title: 'ID', dataIndex: 'id', key: 'id', width: 60},
                            {title: '主规则包', dataIndex: 'mainRulePackagePath', key: 'main', ellipsis: true},
                            {title: '陪跑规则包', dataIndex: 'shadowRulePackagePath', key: 'shadow', ellipsis: true},
                            {title: '陪跑流程ID', dataIndex: 'shadowFlowId', key: 'flow', render: (v: string) => v || '同主流程'},
                            {title: '采样率(%)', dataIndex: 'sampleRate', key: 'rate', width: 90},
                            {title: '状态', dataIndex: 'enabled', key: 'enabled',
                                render: (v: boolean, c: ShadowConfig) => (
                                    <Tag style={{cursor: 'pointer'}} color={v ? 'success' : 'default'}
                                         onClick={() => this.props.dispatch(action.toggleShadowConfig(c.id, !c.enabled))}>
                                        {v ? '启用' : '停用'}
                                    </Tag>
                                )},
                            {title: '操作', key: 'actions',
                                render: (_: unknown, c: ShadowConfig) => (
                                    <Button size="small" danger onClick={() => this.props.dispatch(action.deleteShadowConfig(c.id))}>删除</Button>
                                )},
                        ]}/>
                )}

                {/* 创建表单 */}
                <div style={{marginTop: 10, padding: 10, background: '#f9f9f9', borderRadius: 4, border: '1px solid #eee'}}>
                    <h6 style={{fontWeight: 600, marginBottom: 8}}>创建陪跑配置</h6>
                    <div style={{display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8}}>
                        <input id="shadow-main-path" placeholder="主规则包路径" style={{flex: 1, minWidth: 150, padding: '4px 8px', fontSize: 12, border: '1px solid #ddd', borderRadius: 3}}/>
                        <input id="shadow-shadow-path" placeholder="陪跑规则包路径" style={{flex: 1, minWidth: 150, padding: '4px 8px', fontSize: 12, border: '1px solid #ddd', borderRadius: 3}}/>
                        <input id="shadow-flow-id" placeholder="陪跑流程ID(可选)" style={{width: 120, padding: '4px 8px', fontSize: 12, border: '1px solid #ddd', borderRadius: 3}}/>
                        <input id="shadow-sample-rate" placeholder="采样率(%)" type="number" min="0" max="100" style={{width: 80, padding: '4px 8px', fontSize: 12, border: '1px solid #ddd', borderRadius: 3}}/>
                    </div>
                    <Button type="primary" size="small" onClick={() => {
                        const mainPath = (document.getElementById('shadow-main-path') as HTMLInputElement).value;
                        const shadowPath = (document.getElementById('shadow-shadow-path') as HTMLInputElement).value;
                        const flowId = (document.getElementById('shadow-flow-id') as HTMLInputElement).value;
                        const sampleRate = (document.getElementById('shadow-sample-rate') as HTMLInputElement).value;
                        if (!mainPath || !shadowPath) {
                            alert('请填写主规则包路径和陪跑规则包路径');
                            return;
                        }
                        this.props.dispatch(action.createShadowConfig({
                            mainRulePackagePath: mainPath,
                            shadowRulePackagePath: shadowPath,
                            shadowFlowId: flowId || null,
                            sampleRate: sampleRate ? parseInt(sampleRate) : 100
                        }));
                    }}>
                        <PlusOutlined style={{marginRight: 4}} />
                        创建配置
                    </Button>
                </div>
            </div>
        );
    }

    renderShadowComparisons(comparisons: ShadowComparison[], loading: boolean, stats: ShadowStats | null) {
        return (
            <div>
                <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10}}>
                    <h6 style={{fontWeight: 600, margin: 0}}>陪跑对比结果</h6>
                    {stats && (
                        <div style={{display: 'flex', gap: 15, fontSize: 12, color: '#666'}}>
                            <span>总对比: <b>{stats.totalComparisons}</b></span>
                            <span>差异: <b style={{color: stats.totalDivergent > 0 ? '#f5222d' : '#52c41a'}}>{stats.totalDivergent}</b></span>
                            <span>差异率: <b>{stats.divergenceRate}%</b></span>
                        </div>
                    )}
                </div>

                {loading ? (
                    <div style={{textAlign: 'center', padding: 20}}>加载中...</div>
                ) : (!comparisons || comparisons.length === 0) ? (
                    <div style={{textAlign: 'center', padding: 20, color: '#999'}}>暂无对比数据</div>
                ) : (
                    <Table<ShadowComparison> rowKey="id" dataSource={comparisons} pagination={false} size="small"
                        columns={[
                            {title: '用户ID', dataIndex: 'userId', key: 'user'},
                            {title: '订单号', dataIndex: 'orderNo', key: 'order'},
                            {title: '主流程耗时', dataIndex: 'mainTotalTimeMs', key: 'mainMs', render: (v: number) => `${v}ms`},
                            {title: '陪跑耗时', dataIndex: 'shadowTotalTimeMs', key: 'shadowMs', render: (v: number) => `${v}ms`},
                            {title: '状态一致', dataIndex: 'statusMatch', key: 'sm',
                                render: (v: boolean) => <span style={{color: v ? 'var(--rf-success)' : 'var(--rf-danger)'}}>{v ? '✓' : '✗'}</span>},
                            {title: '结果一致', dataIndex: 'resultMatch', key: 'rm',
                                render: (v: boolean) => <span style={{color: v ? 'var(--rf-success)' : 'var(--rf-danger)'}}>{v ? '✓' : '✗'}</span>},
                            {title: '差异级别', dataIndex: 'divergenceSeverity', key: 'sev',
                                render: (s: string) => {
                                    const color = {HIGH: 'error', MEDIUM: 'warning', LOW: 'processing', NONE: 'success'} as Record<string, string>;
                                    return <Tag color={color[s] || 'default'}>{s}</Tag>;
                                }},
                            {title: '时间', dataIndex: 'createdAt', key: 'time'},
                        ]}/>
                )}
            </div>
        );
    }
}

type MapStateToProps = ReleaseState;

export default connect((state: { release: ReleaseState; ui?: { projectName?: string | null } }): MapStateToProps => ({
    ...state.release,
    // 注入 frame store ui.projectName(替代 window._projectName)
    projectName: (state.ui && state.ui.projectName) || undefined,
}))(ReleasePanel);
