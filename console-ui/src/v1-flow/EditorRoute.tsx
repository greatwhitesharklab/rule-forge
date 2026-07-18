import {useSearchParams} from 'react-router-dom';
import FlowDesigner from './FlowDesigner';

/**
 * V1 决策流设计器画布本体。
 * 可被路由壳或应用内标签宿主渲染 — file 从 props 传入,不依赖 react-router。
 * demo 免登录用法传空串(与路由壳无 file 参数时一致),纯客户端不调后端。
 */
export function V1FlowDesigner({file}: {file: string}) {
    return <FlowDesigner file={file}/>;
}

/**
 * V1 决策流设计器 SPA 路由入口(V7.0.0)。
 *
 * <p>URL:
 * <ul>
 *   <li>{@code /v1-flow}(demo,免登录,空画布)— 纯客户端,不调后端</li>
 *   <li>{@code /app/v1-flow?file=/proj/loan.json}(生产,authed)— 从项目树点 V1 决策流文件进入,
 *       按 file 加载 RuleAsset(JSON via /frame/fileSource)→ 画布编辑 → 保存回</li>
 * </ul>
 *
 * <p>深度"替"老编辑器:文件树点 .json → 本路由 → V1 画布(替代老独立编辑器)。
 *
 * <p>本壳只做 searchParams 取值,画布逻辑全部在 {@link V1FlowDesigner}。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <V1FlowDesigner file={file}/>;
}
