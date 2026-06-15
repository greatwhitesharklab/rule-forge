/**
 * V5.78.3 — DRL Monaco editor wrapper。
 *
 * <p>替换 V5.45.3 的 CodeMirror 5 wrapper,接 console-app 后端
 * {@code /api/ide/*} 端点实现 IDE-grade 编辑体验:
 * <ul>
 *   <li><b>Monarch tokenizer</b> — DRL 关键字 / 注释 / 字符串高亮
 *       (见 {@link drlMonarchLanguage})</li>
 *   <li><b>CompletionProvider</b> — autocomplete 调
 *       {@link completeDrl},kind 走 LSP CompletionItemKind 编号</li>
 *   <li><b>HoverProvider</b> — hover popup 调 {@link hoverDrl},
 *       markdown 渲染</li>
 *   <li><b>setModelMarkers</b> — diagnostics 红线调 {@link parseDrl},
 *       300ms debounce</li>
 * </ul>
 *
 * <p>API 跟 V5.45.3 {@link DrlCodeMirror} 同形(getValue / setValue /
 * onChange)保持 React 父组件 (index.tsx) 改动最小。
 *
 * <p>Phase 14 Option B 选型(custom JSON-RPC + Monaco),不走 full
 * lsp4j / WebSocket。
 *
 * @since 5.78
 */
import * as React from 'react';
import { useEffect, useRef } from 'react';
import Editor, { OnMount, Monaco } from '@monaco-editor/react';
import type { editor, languages } from 'monaco-editor';

import { parseDrl, completeDrl, hoverDrl, SyntaxErrorItem, CompletionItem } from '../../api/drlIde';
import { DRL_LANGUAGE_ID, DRL_MONARCH_LANGUAGE } from './drlMonarchLanguage';

// CompletionItemKind 编号(跟 LSP 一致)— V5.78.3 后端 DrlIdeService
// 走这套编号,前端 provider 直接映射,不动魔数。
const KIND_KEYWORD = 14;
const KIND_FIELD = 5;

export interface DrlMonacoProps {
    /** 父容器 — V5.78.3 用 @monaco-editor/react 的 height=100% 自行撑满,
     *  父 div 需要 fixed height(父组件 index.tsx 已设 height: 100%) */
    initialValue?: string;
    /** 内容变更回调(给 dirty tracking 用) */
    onChange?: (value: string) => void;
}

export const DrlMonaco: React.FC<DrlMonacoProps> = ({ initialValue = '', onChange }) => {
    const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
    const monacoRef = useRef<Monaco | null>(null);
    const debounceRef = useRef<number | null>(null);

    /**
     * V5.78.3 — Monaco 挂载后:
     * 1) 注册 DRL Monarch language
     * 2) 注册 completion / hover provider
     * 3) 订阅 model change 触发 diagnostics(parse + setModelMarkers,debounce 300ms)
     */
    const handleMount: OnMount = (ed, monaco) => {
        editorRef.current = ed;
        monacoRef.current = monaco;

        // 1. 注册 DRL language
        if (!monaco.languages.getLanguages().some((l: languages.ILanguageExtensionPoint) => l.id === DRL_LANGUAGE_ID)) {
            monaco.languages.register({ id: DRL_LANGUAGE_ID });
            monaco.languages.setMonarchTokensProvider(DRL_LANGUAGE_ID, DRL_MONARCH_LANGUAGE);
        }

        // 2. completion provider — 调 console /api/ide/complete
        monaco.languages.registerCompletionItemProvider(DRL_LANGUAGE_ID, {
            triggerCharacters: [' ', '\n', '('],
            provideCompletionItems: async (model: editor.ITextModel, position: PositionLike) => {
                const content = model.getValue();
                const caretOffset = model.getOffsetAt(position);
                try {
                    const resp = await completeDrl(content, caretOffset);
                    // 后端返全部 candidate(不预 prefix-filter),前端用 word at
                    // position 做 prefix match 减少噪音
                    const word = model.getWordUntilPosition(position);
                    return {
                        suggestions: resp.completions.map((c: CompletionItem) => ({
                            label: c.label,
                            kind: mapKind(c.kind),
                            detail: c.detail,
                            insertText: c.label,
                            range: {
                                startLineNumber: position.lineNumber,
                                endLineNumber: position.lineNumber,
                                startColumn: word.startColumn,
                                endColumn: word.endColumn,
                            },
                        })),
                    };
                } catch (e) {
                    // 失败不弹 — IDE 体验优先
                    return { suggestions: [] };
                }
            },
        });

        // 3. hover provider — 调 console /api/ide/hover
        monaco.languages.registerHoverProvider(DRL_LANGUAGE_ID, {
            provideHover: async (model: editor.ITextModel, position: PositionLike) => {
                const content = model.getValue();
                try {
                    const resp = await hoverDrl(content, position.lineNumber - 1, position.column - 1);
                    if (resp.contents) {
                        return { contents: [{ value: resp.contents }] };
                    }
                } catch (e) {
                    // silent — IDE 体验优先
                }
                return null;
            },
        });

        // 4. diagnostics — 监听 model change,debounce 300ms 调 /api/ide/parse
        ed.onDidChangeModelContent(() => {
            if (debounceRef.current !== null) {
                window.clearTimeout(debounceRef.current);
            }
            debounceRef.current = window.setTimeout(async () => {
                const content = ed.getValue();
                try {
                    const resp = await parseDrl(content);
                    const markers = resp.errors.map((err: SyntaxErrorItem) => ({
                        severity: monaco.MarkerSeverity.Error,
                        message: err.message,
                        startLineNumber: err.line,
                        endLineNumber: err.line,
                        startColumn: err.column + 1,   // 0-based → 1-based (Monaco 约定)
                        endColumn: err.column + 2,
                    }));
                    monaco.editor.setModelMarkers(ed.getModel()!, DRL_LANGUAGE_ID, markers);
                } catch (e) {
                    // silent
                }
            }, 300);
        });
    };

    // 卸载清理
    useEffect(() => {
        return () => {
            if (debounceRef.current !== null) {
                window.clearTimeout(debounceRef.current);
            }
        };
    }, []);

    return (
        <Editor
            height="100%"
            defaultLanguage={DRL_LANGUAGE_ID}
            defaultValue={initialValue}
            theme="vs"
            onMount={handleMount}
            onChange={(value) => onChange?.(value ?? '')}
            options={{
                minimap: { enabled: false },
                fontSize: 14,
                lineNumbers: 'on',
                scrollBeyondLastLine: false,
                automaticLayout: true,
                wordWrap: 'on',
            }}
        />
    );
};

function mapKind(kind: number): languages.CompletionItemKind {
    // V5.78.3 仅关心 keyword / field;其他 kind 走 Monaco default mapping
    switch (kind) {
        case KIND_KEYWORD: return 14 as languages.CompletionItemKind;   // Keyword
        case KIND_FIELD: return 5 as languages.CompletionItemKind;     // Field
        default: return kind as languages.CompletionItemKind;
    }
}

// V5.78.3 — Monaco provider 回调签名(行/列 1-based)用 monaco.Position 的子集,
// 局部类型避免循环 import
interface PositionLike {
    lineNumber: number;
    column: number;
}
