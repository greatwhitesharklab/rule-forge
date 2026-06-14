import {useSearchParams} from 'react-router-dom';
import {buildProjectNameFromFile} from '@/Utils';
import DecisionTreeApp from './DecisionTreeApp';
import {DirtyContext, ProjectContext, useDirtyApi} from '../../../editor/EditorContexts';

/**
 * decisiontree React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/decisiontree?file=/project/xxx.dt.xml`。从 query 读 file 传给
 * DecisionTreeApp。替代原 `editor.html?type=decisiontree&file=...`(iframe + Raphael
 * 画布),走 SPA 路由 + react-flow 画布。
 *
 * <p>本路由提供 {@link ProjectContext}(当前编辑文件所属项目名)+ {@link DirtyContext}
 * (dirty 通知接口),替代历史 {@code window._project} / {@code window._setDirty} / {@code window._dirty}。
 * dirty 状态由 {@link useDirtyApi} hook 构造,文件路径变化时自动清零。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = buildProjectNameFromFile(file);
    const {dirtyApi} = useDirtyApi(file);

    return (
        <ProjectContext.Provider value={project}>
            <DirtyContext.Provider value={dirtyApi}>
                <DecisionTreeApp file={file}/>
            </DirtyContext.Provider>
        </ProjectContext.Provider>
    );
}