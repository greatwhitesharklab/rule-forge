import { useSearchParams } from 'react-router-dom';
import DecisionTableEditor from './DecisionTableEditor';

/**
 * 决策表 React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/decisiontable?file=/project/xxx.dtx.xml`。从 query 读 file
 * 传给 DecisionTableEditor。替代原 `editor.html?type=decisionTable`(iframe),
 * 走 SPA 路由。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <DecisionTableEditor file={file}/>;
}
