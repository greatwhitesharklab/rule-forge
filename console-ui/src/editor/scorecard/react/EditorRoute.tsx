import {useEffect, useMemo, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {buildProjectNameFromFile} from '@/Utils';
import ScoreCardEditor from './ScoreCardEditor';
import {DirtyApi, DirtyContext, ProjectContext} from '../../../editor/EditorContexts';

/**
 * 评分卡 React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/scorecard?file=/project/xxx.sc.xml`。从 query 读 file
 * 传给 ScoreCardEditor。替代原 `editor.html?type=scorecardLib`(iframe),
 * 走 SPA 路由。
 *
 * <p>本路由提供 {@link ProjectContext}(当前编辑文件所属项目名)+ {@link DirtyContext}
 * (dirty 通知接口),替代历史 {@code window._project} / {@code window._setDirty}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = buildProjectNameFromFile(file);

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

    useEffect(() => {
        dirtyRef.current = false;
        setDirtyState(false);
    }, [file]);

    return (
        <ProjectContext.Provider value={project}>
            <DirtyContext.Provider value={dirtyApi}>
                <ScoreCardEditor file={file}/>
            </DirtyContext.Provider>
        </ProjectContext.Provider>
    );
}