import { useSearchParams } from 'react-router-dom';
import ScriptDecisionTableEditor from './ScriptDecisionTableEditor';

/**
 * 脚本式决策表 React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/scriptdecisiontable?file=/project/xxx.sdt.xml`。从 query 读
 * file 传给 ScriptDecisionTableEditor。替代原 `editor.html?type=scriptdecisiontable`
 * (iframe),走 SPA 路由。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <ScriptDecisionTableEditor file={file}/>;
}
