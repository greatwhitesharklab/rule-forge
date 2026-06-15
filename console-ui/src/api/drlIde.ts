/**
 * V5.78.3 — DRL IDE 端点 client。
 *
 * <p>调 console-app {@code /api/ide/*} 三个端点(parse / complete / hover),
 * 给 console-ui Monaco editor 用。所有方法返 Promise,失败 throw — caller
 * (DrlMonaco) 自行 catch 转成"no diagnostics"或"no hover"。
 *
 * <p>path 拼写跟 {@code vite.config.ts} proxy 配套:/api → console 8180
 * → /ruleforge/ide/*(DrlIdeController @RequestMapping)。
 *
 * @since 5.78
 */

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
 * POST /api/ide/parse
 *
 * <p>后端 @ 500ms 量级;live editing 走 300ms debounce 后调,典型
 * DRL 文件 1KB 内 5ms 出。
 */
export async function parseDrl(content: string): Promise<ParseResponse> {
    const resp = await fetch('/api/ide/parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
    });
    if (!resp.ok) {
        throw new Error(`ide/parse ${resp.status}: ${await resp.text()}`);
    }
    return resp.json();
}

export async function completeDrl(content: string, caretOffset: number): Promise<CompleteResponse> {
    const resp = await fetch('/api/ide/complete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, caretOffset }),
    });
    if (!resp.ok) {
        throw new Error(`ide/complete ${resp.status}: ${await resp.text()}`);
    }
    return resp.json();
}

export async function hoverDrl(content: string, line: number, col: number): Promise<HoverResponse> {
    const resp = await fetch('/api/ide/hover', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, line, col }),
    });
    if (!resp.ok) {
        throw new Error(`ide/hover ${resp.status}: ${await resp.text()}`);
    }
    return resp.json();
}
