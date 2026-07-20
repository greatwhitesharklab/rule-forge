import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {createEditorStore} from '../store/createEditorStore';
import {connect, Provider} from 'react-redux';
import reducer from './reducer';
import ClientConfigEditor from './component/ClientConfigEditor';
import * as action from './action';
import EditorLoadState from '../editor/EditorLoadState';

interface ClientRootState {
    data: Array<{ name?: string; client?: string }>;
    loaded?: boolean;
    loadError?: unknown;
}

interface LoadGateProps {
    loaded?: boolean;
    loadError?: unknown;
    project: string;
    dispatch: (a: unknown) => unknown;
}

/**
 * 加载态门卫:失败 → 统一错误态(EditorLoadState,带重试);未加载完成 → loading;
 * 就绪 → 编辑器本体。
 */
function LoadGate({loaded, loadError, project, dispatch}: LoadGateProps) {
    if (loadError) {
        return (
            <EditorLoadState
                status="error"
                error={loadError}
                onRetry={() => (dispatch as Function)(action.loadData(project))}
            />
        );
    }
    if (!loaded) {
        return <EditorLoadState status="loading"/>;
    }
    return <ClientConfigEditor project={project}/>;
}

// client 模块是单 reducer(非 combineReducers),state 即 ClientConfigState
const ConnectedLoadGate = connect((state: ClientRootState) => ({
    loaded: state.loaded,
    loadError: state.loadError,
}))(LoadGate);

/**
 * 客户端推送配置 (client) 编辑器本体:store 创建(createEditorStore 统一工厂)+
 * loadData(project) 加载 + EditorLoadState 门禁 + Provider 包裹。
 * 可被路由壳或应用内标签宿主渲染 — project 从 props 传入,不依赖 react-router。
 */
export function ClientEditor({project}: {project: string}) {
    const store = useMemo(() => createEditorStore(reducer), []);
    useEffect(() => {
        if (!project) {
            return;
        }
        (store.dispatch as Function)(action.loadData(project));
    }, [project, store]);

    if (!project) {
        return (
            <EditorLoadState
                status="no-file"
                emptyDescription="未指定项目 — 请从主界面打开客户端推送配置。"
            />
        );
    }

    return (
        <Provider store={store}>
            <ConnectedLoadGate project={project}/>
        </Provider>
    );
}

/**
 * 客户端推送配置 (client) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/client?project=<projectName>}。
 * 注意:client 原先读 {@code ?project=} 而非 {@code ?file=}。本路由保留
 * {@code project} 参数;若 URL 带的是 {@code file}(/project/<name> 形式)则取其最后
 * 一段作为 project 名,保持两种入口都能用。
 * 本壳只做 searchParams 取值(含 file → project 的回退推导),编辑器逻辑全部在
 * {@link ClientEditor}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = params.get('project') || (file ? file.split('/').pop() : '');
    return <ClientEditor project={project}/>;
}
