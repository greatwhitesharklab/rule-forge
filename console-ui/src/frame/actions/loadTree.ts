// V7.24:从 frame/action.ts 拆分 — 项目树加载 thunk(loadData / loadChildren)
import {formPost, jsonPost} from '../../api/client.js';
import * as event from '../event.js';
import * as componentEvent from '../../components/componentEvent.js';
import {LOAD_END, LOAD_CHILDREN_END} from './constants.js';
import {readUiFilters, _setDispatch, _setGetState} from './shared.js';
import {buildData} from './treeNode.js';

// ---- Thunk action creators ----

let _loadDataRequestId = 0;

let _pathsToExpand: string[] = [];

export function loadData(classify?: boolean | null, projectName?: string | null, types?: string | null, searchFileName?: string | null, pathsToExpand?: string[]) {
    if (classify === null || classify === undefined) {
        classify = true;
    }
    if (pathsToExpand) {
        _pathsToExpand = pathsToExpand;
    }
    const requestId = ++_loadDataRequestId;
    return function (dispatch: Function, getState: Function) {
        _setDispatch(dispatch);
        _setGetState(getState);
        const params: Record<string, string> = {};
        if (classify !== undefined && classify !== null) params.classify = String(classify);
        if (projectName !== undefined && projectName !== null) params.projectName = projectName;
        if (types !== undefined && types !== null) params.types = types;
        if (searchFileName !== undefined && searchFileName !== null) params.searchFileName = searchFileName;
        params.projectDetail = 'true';

        formPost('/frame/loadProjects', params).then(function (data: {
            classify: boolean;
            repo: { rootFile: TreeNodeData; publicResource: TreeNodeData; projectNames: string[] };
            user: { import: boolean; export: boolean };
        }) {
            // Skip if a newer request has been made
            if (requestId !== _loadDataRequestId) return;

            const {classify, repo, user} = data;
            const {rootFile, publicResource, projectNames} = repo;
            event.eventEmitter.emit(event.CHANGE_CLASSIFY, classify);
            // Update project list whenever available (needed for project creation auto-select)
            if (projectNames && projectNames.length > 0) {
                event.eventEmitter.emit(event.PROJECT_LIST_CHANGE, projectNames);
            }

            // Determine which project to show in the tree
            const targetProject = projectName || (projectNames && projectNames[0]) || null;

            // Extract the target project's children to show directly (skip root + project layers)
            let treeData: TreeNodeData;
            if (targetProject && rootFile && Array.isArray(rootFile.children)) {
                const projectNode = rootFile.children.find(
                    child => child.name === targetProject || child.fullPath === '/' + targetProject
                );
                if (projectNode && projectNode.children) {
                    // Build a virtual root that contains the project's children directly
                    treeData = {
                        id: rootFile.id,
                        name: rootFile.name,
                        type: 'root',
                        fullPath: rootFile.fullPath,
                        children: projectNode.children
                    };
                } else {
                    treeData = rootFile;
                }
            } else {
                treeData = rootFile;
            }

            buildData(treeData, 1, user);
            buildData(publicResource, 1, user);

            // Mark nodes that should be force-expanded
            if (_pathsToExpand.length > 0) {
                function markForceExpand(node: TreeNodeData | null) {
                    if (node && _pathsToExpand.includes(node.fullPath)) {
                        node._forceExpand = true;
                    }
                    if (node && node.children) {
                        node.children.forEach(markForceExpand);
                    }
                }
                markForceExpand(treeData);
                _pathsToExpand = [];
            }

            dispatch({data: treeData, publicResource: publicResource, type: LOAD_END});
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);

            // V7.7.2:树节点 published 徽标 — 收集项目下所有 V1 节点 fullPath,
            // 单次 POST /v1/publish/status-batch 拿发布状态,回填 _publishedVersion /
            // _publishedStatus,FileTreeNode 据此渲染 "已发布 vX.X.X" 徽标。
            // 非 V1 节点忽略。
            const v1Types = new Set(['v1flow', 'v1library', 'v1ruleset', 'v1decisiontable', 'v1scorecard']);
            const v1Paths: string[] = [];
            function collectV1Paths(node: TreeNodeData | null) {
                if (!node) return;
                if (v1Types.has(node.type)) v1Paths.push(node.fullPath);
                if (node.children) node.children.forEach(collectV1Paths);
            }
            collectV1Paths(treeData);
            if (targetProject && v1Paths.length > 0) {
                jsonPost<Record<string, {status: string; currentVersion: string | null; publishTime: string | null}>>(
                    '/v1/publish/status-batch?project=' + encodeURIComponent(targetProject),
                    v1Paths
                ).then((statusMap) => {
                    if (requestId !== _loadDataRequestId) return;
                    if (!statusMap) return;
                    function enrich(node: TreeNodeData | null) {
                        if (!node) return;
                        if (v1Types.has(node.type)) {
                            const s = statusMap[node.fullPath];
                            if (s) {
                                node._publishedStatus = s.status;
                                node._publishedVersion = s.currentVersion;
                            }
                        }
                        if (node.children) node.children.forEach(enrich);
                    }
                    enrich(treeData);
                    dispatch({data: treeData, publicResource: publicResource, type: LOAD_END});
                }).catch(() => { /* ignore: published 徽标 best-effort,失败不阻塞树 */ });
            }

            // 控制所有节点显示
            const spanEl = document.getElementById('node-' + rootFile.id);
            if (spanEl) {
                const parentLi = spanEl.parentElement;
                if (searchFileName == null || searchFileName === '') {
                    var deepLiChildren = parentLi!.querySelectorAll('ul > li > ul > li');
                    deepLiChildren.forEach(function(child) { (child as HTMLElement).style.display = 'none'; });
                    parentLi!.querySelectorAll('ul > li').forEach(function(item) {
                        const firstI = item.querySelector('i:first-child');
                        if (firstI) { firstI.classList.add('rf-plus'); firstI.classList.remove('rf-minus'); }
                    });
                } else {
                    var allLiChildren = parentLi!.querySelectorAll('li');
                    allLiChildren.forEach(function(child) { (child as HTMLElement).style.display = ''; });
                    parentLi!.querySelectorAll('ul > li').forEach(function(item) {
                        const firstI = item.querySelector('i:first-child');
                        if (firstI) { firstI.classList.add('rf-minus'); firstI.classList.remove('rf-plus'); }
                    });
                }
            }
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}



// 加载子菜单的函数。classify/projectName/types 可省略,thunk 自动从 store 读
// (替代 TreeItem 历史上传 window._classify / window._types)。
export function loadChildren(parentNodeData: TreeNodeData, classify?: boolean | null, projectName?: string | null, types?: string | null) {
    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
    return function (dispatch: Function, getState: Function) {
        _setDispatch(dispatch);
        _setGetState(getState);
        const uiFilters = readUiFilters(getState);
        const effectiveClassify = classify !== undefined && classify !== null ? classify : uiFilters.classify;
        const effectiveProjectName = projectName !== undefined ? projectName : uiFilters.projectName;
        const effectiveTypes = types !== undefined ? types : uiFilters.types;
        formPost('/frame/loadProjects', {
            classify: String(effectiveClassify),
            projectName: effectiveProjectName || '',
            types: effectiveTypes || '',
            parentPath: parentNodeData.fullPath,
            loadChildren: 'true'
        }).then(function (data: {
            repo: { rootFile: TreeNodeData; publicResource: TreeNodeData };
            user: { import: boolean; export: boolean };
        }) {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            const {repo, user} = data;

            // 从 repo 中提取子菜单数据
            let childrenData: TreeNodeData[] | null = null;

            // 根据父节点的路径来确定从哪里提取子菜单数据
            if (parentNodeData.fullPath === '/') {
                // 如果是根节点，从 rootFile.children 获取
                childrenData = repo.rootFile ? repo.rootFile.children! : [];
            } else if (parentNodeData.type === 'publicResource') {
                // 如果是公共资源，从 publicResource.children 获取
                childrenData = repo.publicResource ? repo.publicResource.children! : [];
            } else {
                // 如果是项目节点，需要从 rootFile.children 中找到对应的项目
                if (repo.rootFile && repo.rootFile.children) {
                    const projectNode = repo.rootFile.children.find(child =>
                        child.name === parentNodeData.name ||
                        child.fullPath === parentNodeData.fullPath
                    );
                    childrenData = projectNode ? projectNode.children! : [];
                }
            }

            if (childrenData && childrenData.length > 0) {
                // 为每个子节点构建数据
                childrenData.forEach(child => {
                    buildData(child, (parentNodeData._level || 0) + 1, user);
                });

                dispatch({
                    parentNodeData,
                    childrenData,
                    type: LOAD_CHILDREN_END
                });
            }
        }).catch(function () {
            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
        });
    };
}
