import { useSearchParams } from 'react-router-dom';
import ScoreCardEditor from './ScoreCardEditor';

/**
 * 评分卡 React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/scorecard?file=/project/xxx.sc.xml`。从 query 读 file
 * 传给 ScoreCardEditor。替代原 `editor.html?type=scorecardLib`(iframe),
 * 走 SPA 路由。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <ScoreCardEditor file={file}/>;
}
