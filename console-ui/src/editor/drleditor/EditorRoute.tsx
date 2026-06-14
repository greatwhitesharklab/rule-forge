import {useEffect} from 'react';
import {useSearchParams} from 'react-router-dom';
import {buildProjectNameFromFile} from '../../Utils';
import DrlEditor from './index';

/**
 * DRL 编辑器 (DRL 4, .drl) React 编辑器的 SPA 路由入口。
 *
 * <p>URL: {@code /app/editor/drl?file=/project/<path>/foo.drl}。复现
 * {@code editor/drleditor/index.tsx} 的挂载逻辑(设置 {@code window._project} +
 * 渲染 {@code <DrlEditor file={file}/>}),只是去掉 {@code createRoot(#container)},
 * 直接 return JSX。
 *
 * <p>注意:文件加载({@link loadDrlFile})在 {@link DrlEditor} 组件内部 useEffect 触发,
 * 本路由不重复触发。{@code window._project} 仍在此处设置(frame dirty/保存逻辑依赖它)。
 *
 * <p>当前文件树无 .drl 节点入口(见 frame/action.ts buildData 无 {@code case "Drl"});
 * 本路由作为 SPA 路由就绪,待文件树/规则集 cross-navigation 接入后即可打开。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    useEffect(() => {
        (window as unknown as {_project?: string})._project = buildProjectNameFromFile(file);
    }, [file]);

    return <DrlEditor file={file}/>;
}
