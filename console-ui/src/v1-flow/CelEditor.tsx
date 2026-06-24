import MonacoEditor from '@monaco-editor/react';

/**
 * CEL 条件编辑器(Monaco,纯文本)。
 *
 * <p>V1 所有 condition 字段复用:RuleSet.rules[].condition / DecisionTable rows /
 * ScoreCard bands / gateway 出边。返回 boolean 的 CEL 表达式。
 *
 * <p>W3-4:React Query Builder 可视化拼条件 → 生成 CEL,作为 Monaco 之上的切换层
 * (MVP 先 Monaco 直接写 CEL,RQB 可视化模式后续叠加)。
 */
export default function CelEditor({
    value,
    onChange,
    height = 80,
}: {
    value: string;
    onChange: (v: string) => void;
    height?: number;
}) {
    return (
        <div style={{border: '1px solid #d9d9d9', borderRadius: 4, overflow: 'hidden'}}>
            <MonacoEditor
                height={height}
                language='plaintext'
                theme='vs'
                value={value || ''}
                onChange={(v) => onChange(v ?? '')}
                options={{
                    minimap: {enabled: false},
                    lineNumbers: 'off',
                    folding: false,
                    scrollBeyondLastLine: false,
                    fontSize: 12,
                    tabSize: 2,
                    wordWrap: 'on',
                    automaticLayout: true,
                }}
                loading={<div style={{padding: 8, fontSize: 12, color: '#999'}}>CEL 编辑器加载中…</div>}
            />
        </div>
    );
}
