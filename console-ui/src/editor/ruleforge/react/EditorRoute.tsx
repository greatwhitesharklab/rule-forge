import {useSearchParams} from 'react-router-dom';
import RulesetEditor from './RulesetEditor';

/**
 * ruleforge React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/ruleset?file=/project/xxx.rs.xml`。从 query 读 file 传给 RulesetEditor。
 * 替代原 `editor.html?type=ruleset&file=...`(iframe),走 SPA 路由。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <RulesetEditor file={file}/>;
}
