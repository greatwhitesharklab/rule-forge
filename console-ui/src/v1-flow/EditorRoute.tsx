import {useSearchParams} from 'react-router-dom';
import FlowDesigner from './FlowDesigner';

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
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    return <FlowDesigner file={file}/>;
}
