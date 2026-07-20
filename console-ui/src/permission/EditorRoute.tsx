import {useEffect, useMemo} from 'react';
import {createEditorStore} from '../store/createEditorStore';
import {connect, Provider} from 'react-redux';
import reducer, {PermissionRootState} from './reducer';
import PermissionConfigEditor from './components/PermissionConfigEditor';
import * as action from './action';
import EditorLoadState from '../editor/EditorLoadState';

interface LoadGateProps {
    masterData?: unknown[];
    loadError?: unknown;
    dispatch: (a: unknown) => unknown;
}

/**
 * 加载态门卫:失败 → 统一错误态(EditorLoadState,带重试);未加载完成 → loading;
 * 就绪 → 编辑器本体。权限配置是全局单例(无 file 参数),故无 no-file 空态。
 */
function LoadGate({masterData, loadError, dispatch}: LoadGateProps) {
    if (loadError) {
        return (
            <EditorLoadState
                status="error"
                error={loadError}
                onRetry={() => (dispatch as Function)(action.loadMasterData())}
            />
        );
    }
    if (!masterData) {
        return <EditorLoadState status="loading"/>;
    }
    return <PermissionConfigEditor/>;
}

const ConnectedLoadGate = connect((state: PermissionRootState) => ({
    masterData: state.master.masterData,
    loadError: state.master.loadError,
}))(LoadGate);

/**
 * 权限配置 (permission) 编辑器本体:store 创建(createEditorStore 统一工厂)+
 * loadMasterData 加载 + EditorLoadState 门禁 + Provider 包裹。
 * 可被路由壳或应用内标签宿主渲染 — 全局单例、无 props,不依赖 react-router。
 */
export function PermissionEditor() {
    const store = useMemo(() => createEditorStore(reducer), []);
    useEffect(() => {
        // loadMasterData() 无参,与 index.tsx 一致;仅挂载后触发一次。
        (store.dispatch as Function)(action.loadMasterData());
    }, [store]);

    return (
        <Provider store={store}>
            <ConnectedLoadGate/>
        </Provider>
    );
}

/**
 * 权限配置 (permission) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/permission}(无 file 参数 — 权限配置是全局单例,
 * 不是项目文件)。替代原 iframe {@code editor.html?type=permission}。
 *
 * <p>原 iframe 入口从 TopBar 下拉菜单触发;现改为 TopBar 调 openEditorTab 开应用内
 * 编辑器标签(全局单例,file 无关),本路由保留作深链/刷新兜底。
 */
export default function EditorRoute() {
    return <PermissionEditor/>;
}
