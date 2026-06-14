import { useSearchParams } from 'react-router-dom';
import CrossTableEditor from './CrossTableEditor';

/**
 * 交叉决策表 React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/crosstab?file=/project/xxx.ct.xml`。从 query 读 file
 * 传给 CrossTableEditor。替代原 `editor.html?type=crosstab`(iframe),
 * 走 SPA 路由。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <CrossTableEditor file={file}/>;
}
