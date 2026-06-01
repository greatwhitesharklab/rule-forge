import {useState, useEffect} from 'react';

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
 * PackageNavigator: package-centric navigation component.
 * Displays a dropdown of packages, version selector, and branch indicator.
 * Calls /packageeditor/loadPackageTree to get package file tree.
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
        const url = window._server + '/packageeditor/loadPackages';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(r => {
            if (!r.ok) throw r;
            return r.json();
        }).then((data: PackageItem[]) => {
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
        const url = window._server + '/packageeditor/listBranches';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({project}).toString()
        }).then(r => {
            if (!r.ok) throw r;
            return r.json();
        }).then((data: { branches?: string[] }) => {
            setBranches(data.branches || []);
        }).catch(err => {
            console.error('Failed to load branches:', err);
        });
    }, [project]);

    function loadPackageTree(packageId: string, version?: string) {
        if (!project || !packageId) return;
        setLoading(true);
        const url = window._server + '/packageeditor/loadPackageTree';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({
                project,
                packageId,
                version: version || ''
            }).toString()
        }).then(r => {
            if (!r.ok) throw r;
            return r.json();
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

    function handlePackageSelect(e: React.ChangeEvent<HTMLSelectElement>) {
        const packageId = e.target.value;
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

    function handleVersionSelect(e: React.ChangeEvent<HTMLSelectElement>) {
        const version = e.target.value;
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

    return (
        <div className="package-navigator">
            {/* Branch indicator */}
            <div className="branch-indicator" style={{
                padding: '4px 8px',
                background: branchName !== 'main' ? '#fff3cd' : '#d4edda',
                borderBottom: '1px solid #dee2e6',
                fontSize: '12px',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
            }}>
                <i className="rf rf-branch" style={{fontSize: '14px'}}/>
                <span>当前分支: <strong>{branchName}</strong></span>
                {branchName !== 'main' && (
                    <span style={{color: '#856404', marginLeft: '8px'}}>(与 main 的修改将标黄)</span>
                )}
            </div>

            {/* Package selector */}
            <div style={{padding: '8px', borderBottom: '1px solid #dee2e6'}}>
                <label style={{fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '4px'}}>
                    知识包
                </label>
                <select className="form-control" value={selectedPackage?.id || ''} onChange={handlePackageSelect}>
                    <option value="">选择知识包...</option>
                    {packages.map(pkg => (
                        <option key={pkg.id} value={pkg.id}>{pkg.name || pkg.id}</option>
                    ))}
                </select>
            </div>

            {/* Version selector */}
            {selectedPackage && versions.length > 0 && (
                <div style={{padding: '8px', borderBottom: '1px solid #dee2e6'}}>
                    <label style={{fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '4px'}}>
                        版本
                    </label>
                    <select className="form-control" value={currentVersion || ''} onChange={handleVersionSelect}>
                        {versions.map(v => (
                            <option key={v.version} value={v.version}>
                                {v.version} ({v.auditStatus === 90 ? '已审批' : v.auditStatus === 20 ? '审批中' : '待审批'})
                                {' - ' + (v.createUser || '')}
                            </option>
                        ))}
                    </select>
                </div>
            )}

            {/* File list */}
            {selectedPackage && resourceItems.length > 0 && (
                <div style={{padding: '8px'}}>
                    <label style={{fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '4px'}}>
                        决策文件
                    </label>
                    <ul style={{listStyle: 'none', padding: 0, margin: 0}}>
                        {resourceItems.map((item, idx) => (
                            <li key={idx}
                                style={{
                                    padding: '4px 8px',
                                    cursor: 'pointer',
                                    borderBottom: '1px solid #f0f0f0',
                                    fontSize: '13px'
                                }}
                                onClick={() => handleFileClick(item)}
                                onMouseEnter={e => (e.target as HTMLElement).style.background = '#f5f5f5'}
                                onMouseLeave={e => (e.target as HTMLElement).style.background = ''}
                            >
                                <i className="rf rf-file" style={{marginRight: '6px'}}/>
                                {item.name || item.path.split('/').pop()}
                                {item.version && (
                                    <span style={{color: '#999', marginLeft: '8px', fontSize: '11px'}}>
                                        v{item.version}
                                    </span>
                                )}
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            {loading && (
                <div style={{padding: '20px', textAlign: 'center', color: '#999'}}>
                    加载中...
                </div>
            )}
        </div>
    );
}
