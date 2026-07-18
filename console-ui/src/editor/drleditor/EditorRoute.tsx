import {useSearchParams} from 'react-router-dom';
import {buildProjectNameFromFile} from '../../Utils';
import DrlEditor from './index';
import {DirtyContext, ProjectContext, useDirtyApi} from '../../editor/EditorContexts';
import EditorLoadState from '../EditorLoadState';

/**
 * DRL 编辑器 (DRL 4, .drl) 编辑器本体:ProjectContext/DirtyContext 提供 +
 * no-file 门禁 + 渲染 DrlEditor。
 * 可被路由壳或应用内标签宿主渲染 — file 从 props 传入,不依赖 react-router。
 *
 * <p>(命名 DrlEditorView 避开与同目录 ./index 的 DrlEditor 组件重名。)
 * 提供 {@link ProjectContext}(当前编辑文件所属项目名)+ {@link DirtyContext}
 * (dirty 通知接口),替代历史 {@code window._project} / {@code window._setDirty} / {@code window._dirty}。
 * dirty 状态由 {@link useDirtyApi} hook 构造,文件路径变化时自动清零。
 */
export function DrlEditorView({file}: {file: string}) {
    const project = buildProjectNameFromFile(file);
    const {dirtyApi} = useDirtyApi(file);

    // 无 file 参数 → 引导空态(统一走 EditorLoadState)
    if (!file) {
        return <EditorLoadState status="no-file"/>;
    }

    return (
        <ProjectContext.Provider value={project}>
            <DirtyContext.Provider value={dirtyApi}>
                <DrlEditor file={file}/>
            </DirtyContext.Provider>
        </ProjectContext.Provider>
    );
}

/**
 * DRL 编辑器 (DRL 4, .drl) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/drl?file=/project/<path>/foo.drl}。复现
 * {@code editor/drleditor/index.tsx} 的挂载逻辑(渲染 {@code <DrlEditor file={file}/>}),
 * 只是去掉 {@code createRoot(#container)},直接 return JSX。
 *
 * <p>本壳只做 searchParams 取值,编辑器逻辑全部在 {@link DrlEditorView}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <DrlEditorView file={file}/>;
}
