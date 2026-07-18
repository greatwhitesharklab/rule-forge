import {useSearchParams} from 'react-router-dom';
import ScoreCardEditor from './ScoreCardEditor';

/**
 * V1 评分卡编辑器本体。
 * 可被路由壳或应用内标签宿主渲染 — file 从 props 传入,不依赖 react-router。
 */
export function V1ScoreCardEditor({file}: {file?: string}) {
    return <ScoreCardEditor file={file}/>;
}

/**
 * V1 评分卡编辑器路由。/app/v1-scorecard?file=/proj/x.v1sc.json(从项目树"V1评分卡"分类进入)。
 * 本壳只做 searchParams 取值,编辑器逻辑全部在 {@link V1ScoreCardEditor}。
 */
export default function ScoreCardRoute() {
    const [params] = useSearchParams();
    return <V1ScoreCardEditor file={params.get('file') || undefined}/>;
}
