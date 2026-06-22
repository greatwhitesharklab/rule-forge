import {useState, useEffect} from 'react';
import {Select, Tag, Empty} from 'antd';
import {BranchesOutlined, FileOutlined} from '@ant-design/icons';
import {formPost} from '../../api/client.js';

interface PackageItem {
    id: string;
    name: string;
    [key: string]: unknown;
}

interface VersionInfo {
    version: string;
    auditStatus: number;
    createUser?: string;
    [key: string]: unknown;
}

interface ResourceItemInfo {
    path: string;
    name?: string;
    version?: string;
    gitTag?: string;
    [key: string]: unknown;
}

interface PackageNavigatorProps {
    project: string;
    onFileSelect?: (file: { path: string; name: string; version?: string; gitTag?: string }) => void;
    onVersionChange?: (currentVersion: string | null, gitTag?: string) => void;
}

/**
 * V5.101 重设计:知识包视图。原版全内联 Bootstrap 色 + 原生 select + 文件列表 hover bug,
 * 改为 antd Select + Tag(审计状态)+ 精简 branch chip + token 化样式(tailwind-base.css)。
 *
 * <p>数据契约不变:/packageeditor/loadPackages | listBranches | loadPackageTree。
 *
 * <p>V6.13.1:FrameApp 侧栏 (FileTreePanel) 不再使用本组件 (合并到统一 Tree + 版本下拉);
 * 仅 {@code package-editor} 主屏 ({@code package/action.ts:742} loadPackages caller)
 * 仍 import 本组件做完整的 "包 → 分支 → 版本 → 文件" 多级浏览 UI。
 */
export default function PackageNavigator({project, onFileSelect, onVersionChange}: PackageNavigatorProps) {
    const [packages, setPackages] = useState<PackageItem[]>([]);
    const [selectedPackage, setSelectedPackage] = useState<PackageItem | null>(null);
    const [versions, setVersions] = useState<VersionInfo[]>([]);
    const [currentVersion, setCurrentVersion] = useState<string | null>(null);
    const [resourceItems, setResourceItems] = useState<ResourceItemInfo[]>([]);
    const [branches, setBranches] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);

    // Load packages for the project
    useEffect(() => {
        if (!project) return;
        setLoading(true);
        formPost('/packageeditor/loadPackages', {project}).then((data: PackageItem[]) => {
            setPackages(data || []);
            setLoading(false);
        }).catch(err => {
            console.error('Failed to load packages:', err);
            setLoading(false);
        });
    }, [project]);

    // Load branches
    useEffect(() => {
        if (!project) return;
        formPost('/packageeditor/listBranches', {project}).then((data: { branches?: string[] }) => {
            setBranches(data.branches || []);
        }).catch(err => {
            console.error('Failed to load branches:', err);
        });
    }, [project]);

    function loadPackageTree(packageId: string, version?: string) {
        if (!project || !packageId) return;
        setLoading(true);
        formPost('/packageeditor/loadPackageTree', {
            project,
            packageId,
            version: version || ''
        }).then((data: { versions?: VersionInfo[]; currentVersion?: string; resourceItems?: ResourceItemInfo[]; gitTag?: string }) => {
            setVersions(data.versions || []);
            setCurrentVersion(data.currentVersion || null);
            setResourceItems(data.resourceItems || []);
            setLoading(false);
            if (onVersionChange) {
                onVersionChange(data.currentVersion || null, data.gitTag);
            }
        }).catch(err => {
            console.error('Failed to load package tree:', err);
            setLoading(false);
        });
    }

    function handlePackageSelect(packageId: string) {
        const pkg = packages.find(p => p.id === packageId) || null;
        setSelectedPackage(pkg);
        if (packageId) {
            loadPackageTree(packageId);
        } else {
            setVersions([]);
            setResourceItems([]);
            setCurrentVersion(null);
        }
    }

    function handleVersionSelect(version: string) {
        if (selectedPackage) {
            loadPackageTree(selectedPackage.id, version);
        }
    }

    function handleFileClick(item: ResourceItemInfo) {
        if (onFileSelect) {
            onFileSelect({
                path: item.path,
                name: item.name || item.path.split('/').pop()!,
                version: item.version,
                gitTag: item.gitTag
            });
        }
    }

    const currentBranch = branches.find(b => b.startsWith('user/'));
    const branchName = currentBranch ? currentBranch : 'main';
    const isUserBranch = branchName !== 'main';

    // 审计状态 → Tag 配色(已审批 green / 审批中 blue / 待审批 default)
    function versionTag(status: number) {
        if (status === 90) return <Tag color="success">已审批</Tag>;
        if (status === 20) return <Tag color="processing">审批中</Tag>;
        return <Tag>待审批</Tag>;
    }

    return (
        <div className="package-navigator">
            {/* Branch chip:main 中性 / user 分支暖色提示(原版 loud 绿/黄 banner) */}
            <div className={'pkg-branch' + (isUserBranch ? ' is-user' : '')} title={isUserBranch ? '与 main 的修改将标黄' : undefined}>
                <BranchesOutlined/>
                <span className="pkg-branch-label">当前分支</span>
                <strong>{branchName}</strong>
                {isUserBranch && <span className="pkg-branch-note">修改将标黄</span>}
            </div>

            {/* Package selector */}
            <div className="pkg-section">
                <span className="pkg-label">知识包</span>
                <Select
                    value={selectedPackage?.id || undefined}
                    placeholder="选择知识包…"
                    onChange={handlePackageSelect}
                    allowClear
                    size="middle"
                    style={{width: '100%'}}
                    notFoundContent={loading ? '加载中…' : '暂无知识包'}
                    options={packages.map(pkg => ({value: pkg.id, label: pkg.name || pkg.id}))}
                />
            </div>

            {/* Version selector */}
            {selectedPackage && versions.length > 0 && (
                <div className="pkg-section">
                    <span className="pkg-label">版本</span>
                    <Select
                        value={currentVersion || undefined}
                        onChange={handleVersionSelect}
                        size="middle"
                        style={{width: '100%'}}
                        optionLabelProp="label"
                        options={versions.map(v => ({
                            value: v.version,
                            label: v.version,
                            status: v.auditStatus,
                            createUser: v.createUser
                        }))}
                        optionRender={(option) => {
                            const data = option.data as unknown as { status?: number; createUser?: string };
                            return (
                                <div className="pkg-version-option">
                                    <span className="pkg-version-name">{String(option.value)}</span>
                                    {versionTag(data.status ?? 0)}
                                    {data.createUser && <span className="pkg-version-user">{data.createUser}</span>}
                                </div>
                            );
                        }}
                    />
                </div>
            )}

            {/* File list */}
            <div className="pkg-files">
                {selectedPackage && resourceItems.length > 0 ? (
                    <>
                        <span className="pkg-label">决策文件</span>
                        <ul className="pkg-file-list">
                            {resourceItems.map((item, idx) => {
                                const name = item.name || item.path.split('/').pop();
                                return (
                                    <li key={idx} className="pkg-file" onClick={() => handleFileClick(item)}>
                                        <FileOutlined/>
                                        <span className="pkg-file-name">{name}</span>
                                        {item.version && <span className="pkg-file-version">v{item.version}</span>}
                                    </li>
                                );
                            })}
                        </ul>
                    </>
                ) : selectedPackage ? (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无决策文件" style={{margin: '24px 0'}}/>
                ) : (
                    <div className="pkg-hint">选择一个知识包以查看其版本与决策文件</div>
                )}
            </div>
        </div>
    );
}
