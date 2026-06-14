import {useSearchParams} from 'react-router-dom';
import DecisionTreeApp from './DecisionTreeApp';

/**
 * decisiontree React 编辑器的路由入口。
 *
 * <p>URL: `/app/editor/decisiontree?file=/project/xxx.dt.xml`。从 query 读 file 传给
 * DecisionTreeApp。替代原 `editor.html?type=decisiontree&file=...`(iframe + Raphael
 * 画布),走 SPA 路由 + react-flow 画布。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <DecisionTreeApp file={file}/>;
}
