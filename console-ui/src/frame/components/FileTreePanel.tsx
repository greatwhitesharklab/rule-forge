import {Component} from 'react';
import {Select, Tag} from 'antd';
import * as ACTIONS from '@/frame/action.js';
import Tree from '@/components/tree/component/Tree.jsx';
import {selectProjectName} from '@/frame/reducer.ts';
import {formPost} from '@/api/client.js';

interface FileTreePanelProps {
    store: {
        dispatch: (action: unknown) => void;
        getState: () => { ui?: { projectName?: string | null } };
    };
}

interface FileTreePanelState {
    /** 当前选中的版本 id;空字符串 = Working tree (可编辑);非空 = 选定的 git 版本 (只读) */
    selectedVersionId: string;
    /** 当前项目的版本列表 (从 /packageeditor/loadPackages 拉) */
    versions: ResourceVersion[];
    /** 版本数据加载状态 */
    versionsLoading: boolean;
}

interface ResourceVersion {
    id: string;
    name: string;
    version?: string;
    auditStatus?: number;
    gitTag?: string;
    [key: string]: unknown;
}

/**
 * V6.13.1:统一文件树 + 知识包视图。
 *
 * <p>原 FileTreePanel 在 {@code <Tree>} (working tree, 可编辑) 和
 * {@code <PackageNavigator>} (git 历史版本, 只读) 之间 toggle。
 * V6.13.1 合并成单面板,顶部加版本下拉:
 * <ul>
 *   <li>默认 = Working tree → {@code <Tree readOnly={false} draggable={true}/>}</li>
 *   <li>选具体版本 → {@code <Tree readOnly={true} onFileReadOnlyClick=seeFileSource/>},
 *       禁编辑菜单,文件 click 弹源码对话框 (走 git snapshot)</li>
 *   <li>切回 Working tree → 恢复可编辑</li>
 * </ul>
 *
 * <p>选版本时 dispatch {@link ACTIONS.setCurrentGitTag} (与原 PackageNavigator 行为一致,
 * seeFileSource thunk 读 store 拿 gitTag 走 /frame/fileSource?gitTag=...),
 * 切回 Working tree 时 dispatch setCurrentGitTag(null) 清空。
 *
 * <p>{@code <PackageNavigator>} 仍被 {@code package-editor} 主屏 (package/action.ts:742) 使用,
 * 这里只 FileTreePanel 不再引用它。
 *
 * <p>搜索逻辑:仍走 TopBar (V5.101 迁入),setSearchFileName + loadData 触发 reload。
 */
export default class FileTreePanel extends Component<FileTreePanelProps, FileTreePanelState> {
    static WORKING_TREE_ID = '__working_tree__';

    constructor(props: FileTreePanelProps) {
        super(props);
        this.state = {
            selectedVersionId: FileTreePanel.WORKING_TREE_ID,
            versions: [],
            versionsLoading: false,
        };
    }

    componentDidMount(): void {
        // 初次 mount 拉一次 (适配无 project 切换直接打开页面的场景)
        this._loadVersionsIfNeeded();
    }

    componentDidUpdate(prevProps: FileTreePanelProps): void {
        // project 切换 → 重拉版本列表 + 重置回 Working tree
        const prevProject = selectProjectName(prevProps.store.getState() as any);
        const curProject = selectProjectName(this.props.store.getState() as any);
        if (prevProject !== curProject) {
            this.setState({selectedVersionId: FileTreePanel.WORKING_TREE_ID, versions: []});
            this.props.store.dispatch(ACTIONS.setCurrentGitTag(null));
            this._loadVersionsIfNeeded();
        }
    }

    _loadVersionsIfNeeded = (): void => {
        const project = selectProjectName(this.props.store.getState() as any);
        if (!project) {
            return;
        }
        this.setState({versionsLoading: true});
        formPost('/packageeditor/loadPackages', {project})
            .then((resp: {status?: boolean; data?: ResourceVersion[]} | ResourceVersion[]) => {
                const data = Array.isArray(resp) ? resp : (resp && (resp as {data?: ResourceVersion[]}).data) || [];
                this.setState({versions: data as ResourceVersion[], versionsLoading: false});
            })
            .catch(() => {
                this.setState({versionsLoading: false});
            });
    };

    _onVersionChange = (value: string): void => {
        if (value === FileTreePanel.WORKING_TREE_ID) {
            // 切回 Working tree
            this.setState({selectedVersionId: value});
            this.props.store.dispatch(ACTIONS.setCurrentGitTag(null));
            return;
        }
        // 选具体版本
        const version = this.state.versions.find((v) => v.id === value);
        const gitTag = (version && version.gitTag) || value;
        this.setState({selectedVersionId: value});
        this.props.store.dispatch(ACTIONS.setCurrentGitTag(gitTag));
    };

    /** V6.13.1:readOnly 模式下 Tree 文件 click 回调 → dispatch seeFileSource thunk。 */
    _onFileReadOnlyClick = (data: TreeNodeData): void => {
        this.props.store.dispatch(ACTIONS.seeFileSource(data) as unknown);
    };

    render() {
        const project = selectProjectName(this.props.store.getState() as any) || '';
        const {selectedVersionId, versions, versionsLoading} = this.state;
        const readOnly = selectedVersionId !== FileTreePanel.WORKING_TREE_ID;

        // antd Select options:Working tree (默认) + 加载到的版本列表
        const options = [
            {label: 'Working tree', value: FileTreePanel.WORKING_TREE_ID},
            ...versions.map((v) => ({
                label: (
                    <span>
                        {v.name || v.version || v.id}
                        {this._renderAuditTag(v.auditStatus)}
                    </span>
                ),
                value: v.id,
            })),
        ];

        return (
            <div className="file-tree-panel">
                <div className="file-tree-toolbar">
                    <Select
                        className="file-tree-version-selector"
                        data-testid="version-selector"
                        value={selectedVersionId}
                        options={options}
                        onChange={this._onVersionChange}
                        loading={versionsLoading}
                        disabled={!project}
                        placeholder="选择版本"
                        style={{minWidth: 160}}
                    />
                </div>
                <div className="file-tree-content">
                    <Tree
                        readOnly={readOnly}
                        draggable={!readOnly}
                        onFileReadOnlyClick={readOnly ? this._onFileReadOnlyClick : undefined}
                    />
                </div>
            </div>
        );
    }

    /** 沿用 PackageNavigator.versionTag 的 audit status Tag 配色 */
    _renderAuditTag(status: number | undefined): React.ReactNode {
        if (status === 90) return <Tag color="success" style={{marginLeft: 6}}>已审批</Tag>;
        if (status === 20) return <Tag color="processing" style={{marginLeft: 6}}>审批中</Tag>;
        return <Tag style={{marginLeft: 6}}>待审批</Tag>;
    }
}
