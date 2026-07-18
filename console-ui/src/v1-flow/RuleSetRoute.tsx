import {useSearchParams} from 'react-router-dom';
import RuleSetEditor from './RuleSetEditor';

/**
 * V1 规则集编辑器本体。
 * 可被路由壳或应用内标签宿主渲染 — file 从 props 传入,不依赖 react-router。
 */
export function V1RuleSetEditor({file}: {file?: string}) {
    return <RuleSetEditor file={file}/>;
}

/**
 * V1 规则集编辑器路由。/app/v1-ruleset?file=/proj/x.v1rs.json(从项目树"V1规则集"分类进入)。
 * 本壳只做 searchParams 取值,编辑器逻辑全部在 {@link V1RuleSetEditor}。
 */
export default function RuleSetRoute() {
    const [params] = useSearchParams();
    return <V1RuleSetEditor file={params.get('file') || undefined}/>;
}
