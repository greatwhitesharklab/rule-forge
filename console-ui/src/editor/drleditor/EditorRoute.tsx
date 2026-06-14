import {useEffect, useMemo, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {buildProjectNameFromFile} from '../../Utils';
import DrlEditor from './index';
import {DirtyApi, DirtyContext, ProjectContext} from '../../editor/EditorContexts';

/**
 * DRL 编辑器 (DRL 4, .drl) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/drl?file=/project/<path>/foo.drl}。复现
 * {@code editor/drleditor/index.tsx} 的挂载逻辑(渲染 {@code <DrlEditor file={file}/>}),
 * 只是去掉 {@code createRoot(#container)},直接 return JSX。
 *
 * <p>本路由提供 {@link ProjectContext}(当前编辑文件所属项目名)+ {@link DirtyContext}
 * (dirty 通知接口),替代历史 {@code window._project} / {@code window._setDirty} / {@code window._dirty}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = buildProjectNameFromFile(file);

    // dirty tracking — 用 state 触发重新渲染(让 toolbar 反映脏态),用 ref 提供
    // 给 DirtyApi.isDirty() 实时读最新值(避免 closure 拿到旧 state)。
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

    return (
        <ProjectContext.Provider value={project}>
            <DirtyContext.Provider value={dirtyApi}>
                <DrlEditor file={file}/>
            </DirtyContext.Provider>
        </ProjectContext.Provider>
    );
}