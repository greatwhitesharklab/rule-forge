/**
 * V5.78.3 — DRL IDE 端点 client。
 *
 * <p>调 console-app {@code /api/ide/*} 三个端点(parse / complete / hover),
 * 给 console-ui Monaco editor 用。所有方法返 Promise,失败 throw — caller
 * (DrlMonaco) 自行 catch 转成"no diagnostics"或"no hover"。
 *
 * <p>V6.12.4 — 改用集中 {@link jsonPost} (apiBase() 默认 /api, 路径去掉前缀),
 * 错误处理走 client 统一路径。
 *
 * @since 5.78
 */

import {jsonPost} from './client.js';

export interface SyntaxErrorItem {
    line: number;       // 1-based(后端 ANTLR 约定)
    column: number;     // 0-based
    message: string;
}

export interface ParseResponse {
    errors: SyntaxErrorItem[];
    imports: string[];
    rules: { name: string }[];
}

export interface CompletionItem {
    label: string;
    detail: string;
    kind: number;       // LSP CompletionItemKind 编号
}

export interface CompleteResponse {
    completions: CompletionItem[];
}

export interface HoverResponse {
    contents: string | null;   // markdown,null = 无 hover
}

/**
 * POST /ide/parse
 *
 * <p>后端 @ 500ms 量级;live editing 走 300ms debounce 后调,典型
 * DRL 文件 1KB 内 5ms 出。
 */
export function parseDrl(content: string): Promise<ParseResponse> {
    return jsonPost<ParseResponse>('/ide/parse', {content}, {silent: true});
}

export function completeDrl(content: string, caretOffset: number): Promise<CompleteResponse> {
    return jsonPost<CompleteResponse>('/ide/complete', {content, caretOffset}, {silent: true});
}

export async function hoverDrl(content: string, line: number, col: number): Promise<HoverResponse> {
    return jsonPost<HoverResponse>('/ide/hover', {content, line, col}, {silent: true});
}
