import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {formPost} from '@/api/client';
import {buildProjectNameFromFile} from '../../Utils';

/**
 * V6.20.0 P3:DMN 1.3 标准决策模型 — 只读源查看器。
 *
 * <p>URL: {@code /app/editor/dmn?file=/project/<path>/foo.dmn}。
 *
 * <p>后端 core/ir/dmn/DmnResourceDispatcher 已实现 Kie DMN 1.3 编译 → DecisionTable
 * 转换 → RETE rules emission(FEEL 表达式当前以字面量形式落入 SimpleValue.content,
 * 暂不作为 DRL 求值,见 dispatcher 注释)。所以 .dmn 知识包是"可执行但 FEEL 不求值"
 * 的状态;P3 UI 只展示原文供用户审阅 + 让用户从外部编辑器修改覆盖。
 *
 * <p>本组件零编辑能力 — 调 {@code /frame/fileSource} 拿原文,渲染到 {@code <pre>}。
 * 知识包集成由 {@code com.ruleforge.core.builder.KnowledgeBuilder} 透明处理
 * (.dmn 后缀自动 dispatch),用户点 build 即可看到规则入网。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = buildProjectNameFromFile(file);
    const [content, setContent] = useState<string>('');
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);
        formPost('/frame/fileSource', {path: file})
            .then((result: {content: string}) => {
                if (cancelled) return;
                setContent(result.content || '');
                setLoading(false);
            })
            .catch((err: unknown) => {
                if (cancelled) return;
                setError(String(err));
                setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [file]);

    return (
        <div style={{padding: 16, height: '100vh', display: 'flex', flexDirection: 'column'}}>
            <div
                style={{
                    padding: '8px 12px',
                    marginBottom: 12,
                    background: '#fffbe6',
                    border: '1px solid #ffe58f',
                    borderRadius: 4,
                }}
            >
                <strong>DMN 1.3 只读查看器</strong>
                &nbsp;·&nbsp;文件:<code>{file || '(未指定)'}</code>
                &nbsp;·&nbsp;项目:<code>{project}</code>
                <div style={{marginTop: 4, fontSize: 12, color: '#666'}}>
                    DMN 文件不可在 UI 内编辑。请用外部 DMN 建模工具(如 Trisotech、Camunda Modeler)生成,
                    覆盖本文件路径。知识包构建时 core/ir/dmn/DmnResourceDispatcher 自动编译并入网(当前 FEEL 表达式不求值)。
                </div>
            </div>
            {loading && <div>加载中…</div>}
            {error && (
                <div style={{color: '#cf1322'}}>
                    加载失败:{error}
                </div>
            )}
            {!loading && !error && (
                <pre
                    data-testid="dmn-source"
                    style={{
                        flex: 1,
                        margin: 0,
                        padding: 12,
                        background: '#fafafa',
                        border: '1px solid #d9d9d9',
                        borderRadius: 4,
                        overflow: 'auto',
                        fontSize: 13,
                        fontFamily: 'Menlo, Consolas, monospace',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                    }}
                >
                    {content}
                </pre>
            )}
        </div>
    );
}
