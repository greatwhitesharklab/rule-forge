import {useEffect, useMemo, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import FlowEditor from './FlowEditor';
import KnowledgeTreeDialog from '../components/dialog/component/KnowledgeTreeDialog';
import QuickTestDialog from '../components/dialog/component/QuickTestDialog';
import {buildProjectNameFromFile, handleResponseError} from '../Utils';
import {save, saveNewVersion, formPost, apiBase} from '../api/client';
import * as event from '../components/componentEvent';
import * as componentEvent from '../components/componentEvent';
import {alert} from '@/utils/modal';
import {ThunderboltOutlined, UploadOutlined} from '@ant-design/icons';
import {DirtyApi, DirtyContext, ProjectContext} from '@/editor/EditorContexts';

/**
 * 决策流(bpmn-js)编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/flow?file=/project/<path>/foo.rl.xml}。复现
 * {@code flow-bpmn/index.tsx} 的挂载逻辑(工具栏 + 对话框 + BPMN XML 加载 +
 * editorRef 生命周期),只是去掉三个 {@code createRoot(#container)} 分散挂载,
 * 改为单个 React 组件 tree return JSX。
 *
 * <p>布局沿用 {@code editor.html?type=flowbpmn} 的 DOM 结构:
 * <pre>
 *   toolbar(45px) → container(calc(100vh - 45px)) → dialog
 * </pre>
 * 工具栏按钮 / 对话框 / XML fetch 逻辑与 index.tsx 一致,仅从命令式 DOM 改为声明式 JSX +
 * {@code editorRef}({@code useRef})取代闭包变量。
 *
 * <p>dirty tracking(V5.70 新增):
 * <ul>
 *   <li>{@link FlowEditor} 订阅 bpmn-js {@code commandStack.changed} 事件 →
 *       {@code dirtyApi.setDirty()}(任何 modeler 操作都标脏)</li>
 *   <li>{@link FlowEditor} importXML 完成后调 {@code dirtyApi.clearDirty()} (新加载 = 未保存状态)</li>
 *   <li>本路由 {@link saveFlow} 服务端保存成功后 {@code dirtyApi.clearDirty()}</li>
 *   <li>工具栏保存按钮根据 {@code dirtyApi.isDirty()} 显示 *保存 / 保存</li>
 * </ul>
 *
 * <p>本路由提供 {@link ProjectContext}(当前编辑文件所属项目名)+ {@link DirtyContext}
 * (dirty 通知接口),替代历史 {@code window._project} / {@code window._setDirty}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const decodedFile = decodeURIComponent(file);
    const editorRef = useRef<any>(null);
    // 当前文件所属项目(原 window._project)。一次计算,通过 ProjectContext 提供给复用对话框。
    const project = buildProjectNameFromFile(file);

    // dirty api(替代 window._setDirty / window._dirty)。state 触发 toolbar 重渲染,
    // ref 提供 isDirty() 实时读最新值。
    const [, setDirtyState] = useState(false);
    const dirtyRef = useRef(false);
    const dirtyApi = useMemo<DirtyApi>(() => ({
        setDirty: () => {
            if (dirtyRef.current) return;
            dirtyRef.current = true;
            setDirtyState(true);
        },
        clearDirty: () => {
            if (!dirtyRef.current) return;
            dirtyRef.current = false;
            setDirtyState(false);
        },
        isDirty: () => dirtyRef.current,
    }), []);

    // 文件路径变化时清零 dirty(新文件 = 未保存状态)。
    useEffect(() => {
        dirtyRef.current = false;
        setDirtyState(false);
    }, [file]);

    useEffect(() => {
        if (!file || file.length < 1) {
            alert('当前编辑器未指定具体文件！');
            return;
        }
        // Load BPMN XML after editor mounts + onReady fires.
        fetch(apiBase() + '/flow/load?file=' + encodeURIComponent(file)).then(function (response: any) {
            if (!response.ok) throw response;
            return response.text();
        }).then(function (xml: string) {
            if (editorRef.current) {
                editorRef.current.loadXML(xml);
            }
        }).catch(function (response: any) {
            if (response && response.status === 404) {
                // New file - editor already shows empty diagram
            } else {
                handleResponseError(response, '加载决策流失败：');
            }
        });
    }, [file]);

    function openImportDialog(fileType: string) {
        componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: project,
            fileType: fileType,
            callback: function (f: string, version: string) {
                let path = 'jcr:' + f;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                if (!editorRef.current) return;
                editorRef.current.addImport(fileType, path);
            }
        });
    }

    function saveFlow(newVersion: boolean) {
        if (!editorRef.current) return;
        editorRef.current.saveXML().then(function (xml: string) {
            if (!xml) return;
            event.eventEmitter.emit(event.SHOW_LOADING, '数据保存中');
            const postData: Record<string, string> = {content: encodeURIComponent(xml), file: file, newVersion: String(newVersion)};
            const url = apiBase() + '/common/saveFile';
            if (newVersion) {
                saveNewVersion(url, {file, content: encodeURIComponent(xml)}).then(function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                    dirtyApi.clearDirty();
                    alert('保存成功!');
                }).catch(function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                });
            } else {
                save(url, postData).then(function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                    dirtyApi.clearDirty();
                    alert('保存成功!');
                });
            }
        });
    }

    const isDirty = dirtyRef.current;

    return (
        <ProjectContext.Provider value={project}>
            <DirtyContext.Provider value={dirtyApi}>
                {/* 工具栏 — 复现 index.tsx toolbarRoot.render 的内容 */}
                <div className="toolbar">
                    <button className="rf-btn rf-btn-ghost rf-btn-sm" onClick={function () { saveFlow(false); }}>
                        <i className="rf rf-save"/> {isDirty ? '*保存' : '保存'}
                    </button>{' '}
                    <button className="rf-btn rf-btn-ghost rf-btn-sm" onClick={function () { saveFlow(true); }}>
                        <i className="rf rf-savenewversion"/> {isDirty ? '*生成版本' : '生成版本'}
                    </button>{' '}
                    <button className="rf-btn rf-btn-primary rf-btn-sm" onClick={function () {
                        event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {
                            project: project, file: decodedFile
                        });
                    }}>
                        <ThunderboltOutlined /> 快速测试
                    </button>
                    {' | '}
                    <button className="rf-btn rf-btn-ghost rf-btn-sm" onClick={function () { openImportDialog('VariableLibrary'); }}>
                        <i className="rf rf-variable"/> 变量库
                    </button>
                    <button className="rf-btn rf-btn-ghost rf-btn-sm" onClick={function () { openImportDialog('ConstantLibrary'); }}>
                        <i className="rf rf-constant"/> 常量库
                    </button>
                    <button className="rf-btn rf-btn-ghost rf-btn-sm" onClick={function () { openImportDialog('ActionLibrary'); }}>
                        <i className="rf rf-action"/> 动作库
                    </button>
                    <button className="rf-btn rf-btn-ghost rf-btn-sm" onClick={function () { openImportDialog('ParameterLibrary'); }}>
                        <i className="rf rf-parameter"/> 参数库
                    </button>
                    {' | '}
                    <button className="rf-btn rf-btn-success rf-btn-sm" onClick={function () {
                        if (!editorRef.current) return;
                        editorRef.current.saveXML().then(function (xml: string) {
                            if (!xml) return;
                            formPost('/flow/deploy', {file: file}).then(function (data: any) {
                                alert('部署成功! Deployment ID: ' + (data.deploymentId || ''));
                            }).catch(function (response: any) {
                                handleResponseError(response, '部署失败：');
                            });
                        });
                    }}>
                        <UploadOutlined /> 部署
                    </button>
                </div>

                {/* 编辑器 — container 高度复现 editor.html?type=flowbpmn 的 calc(100vh - 45px) */}
                <div style={{width: '100%', height: 'calc(100vh - 45px)'}}>
                    <FlowEditor dirtyApi={dirtyApi} onReady={function (ref: any) { editorRef.current = ref; }}/>
                </div>

                {/* 对话框 — 复现 index.tsx dialogContainer.render */}
                <KnowledgeTreeDialog/>
                <QuickTestDialog/>
            </DirtyContext.Provider>
        </ProjectContext.Provider>
    );
}