import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {createEditorStore} from '../store/createEditorStore';
import {connect, Provider} from 'react-redux';
import reducer, {ResourceRootState} from './reducer';
import ResourceEditor from './components/ResourceEditor';
import * as action from './action';
import * as componentEvent from '../components/componentEvent.js';
import Loading from '../components/loading/component/Loading';
import EditorLoadState from '../editor/EditorLoadState';

interface LoadGateProps {
    error?: unknown;
    file: string;
    dispatch: (a: unknown) => unknown;
}

/**
 * 加载态门卫:加载失败时渲染统一错误态(EditorLoadState,带重试),否则渲染编辑器本体。
 * 加载中的全屏 Spinner 由 {@code <Loading show={true}/>} 覆盖层负责(沿用原逻辑)。
 */
function LoadGate({error, file, dispatch}: LoadGateProps) {
    if (error) {
        return (
            <EditorLoadState
                status="error"
                error={error}
                onRetry={() => {
                    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                    dispatch(action.generateVariableLibrary(file));
                }}
            />
        );
    }
    return <ResourceEditor file={file}/>;
}

const ConnectedLoadGate = connect((state: ResourceRootState) => ({error: state.master.error}))(LoadGate);

/**
 * 公共资源 (resource) 编辑器本体:store 创建(createEditorStore 统一工厂)+
 * generateVariableLibrary 加载 + EditorLoadState 门禁 + Provider 包裹 + Loading 覆盖层。
 * 可被路由壳或应用内标签宿主渲染 — file 从 props 传入,不依赖 react-router。
 *
 * <p>(命名 ResourceEditorView 避开与同目录 components/ResourceEditor 组件重名;
 * 逻辑复现 {@code resource/index.tsx} 的挂载逻辑,替代原 iframe
 * {@code editor.html?type=resource}。)
 */
export function ResourceEditorView({file}: {file: string}) {
    const store = useMemo(() => createEditorStore(reducer), []);
    useEffect(() => {
        if (!file || file.length < 1) {
            return;
        }
        (store.dispatch as Function)(action.generateVariableLibrary(file));
    }, [file, store]);

    if (!file) {
        return <EditorLoadState status="no-file"/>;
    }

    return (
        <div>
            <Loading show={true}/>
            <Provider store={store}>
                <ConnectedLoadGate file={file}/>
            </Provider>
        </div>
    );
}

/**
 * 公共资源 (resource) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/resource?file=/xxx}。
 * 本壳只做 searchParams 取值,编辑器逻辑全部在 {@link ResourceEditorView}。
 * 无 file 参数 → 引导空态;加载失败 → 统一错误态(见 {@link LoadGate})。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <ResourceEditorView file={file}/>;
}
