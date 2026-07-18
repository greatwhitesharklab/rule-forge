import {useEffect, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {formPost} from '@/api/client';
import {buildProjectNameFromFile} from '../../Utils';
import EditorLoadState from '../EditorLoadState';

/**
 * V6.20.0 P3:PMML 4.4 标准预测模型 — 只读源查看器。
 *
 * <p>URL: {@code /app/editor/pmml?file=/project/<path>/foo.pmml}。
 *
 * <p>后端 core/ir/pmml/PmmlResourceDispatcher 已实现 pmml4s 1.5.6 顶层字段填充
 * (ScorecardDefinition: useReasonCodes/initialScore/baselineMethod/reasonCodeAlgorithm;
 *  DecisionTree: missingValueStrategy/defaultChild/splitCharacteristic),
 * 但当前版本仅顶层字段,子结构(cells/rows/&lt;Node&gt; 树)留空 → 0 rules emitted
 * (见 KnowledgeBuilder.java:112-120,return value 主动丢弃)。
 * 全量规则展开留作未来 V5.41.4 子结构扩展 PR。
 *
 * <p>本组件零编辑能力 — 调 {@code /frame/fileSource} 拿原文,渲染到 {@code <pre>}。
 * UI 显式标注"暂不产生规则",避免用户误以为 build 后会触发 PMML 求值。
 */
export default function EditorRoute() {
    const [params] = useSearchParams();
    const file = params.get('file') || '';
    const project = buildProjectNameFromFile(file);
    const [content, setContent] = useState<string>('');
    const [loading, setLoading] = useState<boolean>(true);
    // 保留原始错误对象,交给 EditorLoadState 格式化(禁止 String(err) 显示 [object Response])
    const [error, setError] = useState<unknown>(null);
    // 重试计数:错误态点"重试"时 +1,触发 useEffect 重新加载
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        if (!file) {
            setLoading(false);
            return;
        }
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
                setError(err);
                setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [file, reloadKey]);

    // 无 file 参数 → 引导空态(统一走 EditorLoadState)
    if (!file) {
        return <EditorLoadState status="no-file"/>;
    }

    return (
        <div style={{padding: 16, height: '100vh', display: 'flex', flexDirection: 'column'}}>
            <div
                style={{
                    padding: '8px 12px',
                    marginBottom: 12,
                    background: '#fff1f0',
                    border: '1px solid #ffa39e',
                    borderRadius: 4,
                }}
            >
                <strong>PMML — 仅作导入格式(非核心模型)</strong>
                &nbsp;·&nbsp;文件:<code>{file || '(未指定)'}</code>
                &nbsp;·&nbsp;项目:<code>{project}</code>
                <div style={{marginTop: 4, fontSize: 12, color: '#666'}}>
                    PMML 是传统银行/SAS 体系遗留的模型交换标准,本系统将其作为<strong>导入格式</strong>:
                    接收用户从外部 SAS / SPSS / 银行决策平台导出的 PMML,作为参考或迁移起点。
                    <strong style={{color: '#cf1322'}}>当前不作为核心执行模型</strong> — 后端 dispatcher 仅填充
                    顶层字段(useReasonCodes/initialScore/...),子结构(cells/rows/树节点)尚未展开,
                    知识包构建后此文件 0 rules 触发。全量展开留作未来 V5.41.4 子任务。
                    本文件不可在 UI 内编辑 — 请用外部 PMML 建模工具生成后覆盖本路径。
                    核心模型请使用 DRL / 决策流(本系统的原生表达)。
                </div>
            </div>
            {loading && <EditorLoadState status="loading"/>}
            {error && (
                <EditorLoadState status="error" error={error} onRetry={() => setReloadKey(k => k + 1)}/>
            )}
            {!loading && !error && (
                <pre
                    data-testid="pmml-source"
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
