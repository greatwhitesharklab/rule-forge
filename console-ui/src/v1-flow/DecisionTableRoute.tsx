import {useSearchParams} from 'react-router-dom';
import DecisionTableEditor from './DecisionTableEditor';

/**
 * V1 决策表编辑器本体。
 * 可被路由壳或应用内标签宿主渲染 — file 从 props 传入,不依赖 react-router。
 */
export function V1DecisionTableEditor({file}: {file?: string}) {
    return <DecisionTableEditor file={file}/>;
}

/**
 * V1 决策表编辑器路由。/app/v1-decisiontable?file=/proj/x.v1dt.json(从项目树"V1决策表"分类进入)。
 * 本壳只做 searchParams 取值,编辑器逻辑全部在 {@link V1DecisionTableEditor}。
 */
export default function DecisionTableRoute() {
    const [params] = useSearchParams();
    return <V1DecisionTableEditor file={params.get('file') || undefined}/>;
}
