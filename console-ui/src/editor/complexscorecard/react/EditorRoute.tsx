import { useSearchParams } from 'react-router-dom';
import ComplexScoreCardEditor from './ComplexScoreCardEditor';

/**
 * 复杂评分卡 React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/complexscorecard?file=/project/xxx.complexscorecard`。
 * 从 query 读 file 传给 ComplexScoreCardEditor。替代原
 * `editor.html?type=complexscorecard`(iframe),走 SPA 路由。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <ComplexScoreCardEditor file={file}/>;
}
