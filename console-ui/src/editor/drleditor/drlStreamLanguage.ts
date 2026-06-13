/**
 * V5.45.3 — DRL 4 StreamLanguage for CodeMirror 5 syntax highlighting.
 *
 * <p>基于 {@link import('codemirror').StreamLanguage} 包装 DRL 关键字 + string +
 * comment + number 高亮。V5.45.3 范围:**只**覆盖高频 DRL 4 关键字(rule / when / then /
 * end / import / package / dialect / declare / extends / query / function /
 * @annotation 等),不追求 Drools 6.x 完整 grammar(留 V5.46+ 单独 PR)。
 *
 * <p>正则按 DRL 关键字大小写敏感匹配(DRL 关键字小写,跟 Drools 6.x 兼容)。
 *
 * @since 5.45
 */
import CodeMirror from 'codemirror';

const DRL_KEYWORDS = [
    'package', 'dialect', 'import', 'rule', 'when', 'then', 'end', 'extend',
    'declare', 'extends', 'query', 'function', 'global',
    'salience', 'agenda-group', 'activation-group', 'ruleflow-group',
    'auto-focus', 'no-loop', 'lock-on-active', 'enabled',
    'date-effective', 'date-expires', 'timer',
    'not', 'exists', 'eval', 'from', 'collect', 'accumulate',
    'init', 'action', 'result', 'reverse',
    'in', 'memberof', 'matches', 'contains', 'soundslike',
    'true', 'false', 'null',
    'over', 'window', 'time',
];

export const drlStreamLanguage: CodeMirror.StreamLanguage<string> = {
    startState: () => ({ inString: false }),
    token: (stream, state) => {
        // 字符串字面量
        if (state.inString) {
            if (stream.skipTo('"')) {
                stream.next();
                state.inString = false;
                return 'string';
            }
            stream.skipToEnd();
            return 'string';
        }
        if (stream.eat('"')) {
            state.inString = true;
            return 'string';
        }

        // 单行注释
        if (stream.match(/\/\/.*/)) {
            return 'comment';
        }

        // 多行注释
        if (stream.match(/\/\*/)) {
            while (!stream.eol()) {
                if (stream.match(/\*\//)) return 'comment';
                stream.next();
            }
            return 'comment';
        }

        // 数字
        if (stream.match(/^-?\d+(\.\d+)?/)) {
            return 'number';
        }

        // 关键字
        if (stream.match(/[a-zA-Z_][a-zA-Z0-9_-]*/)) {
            const word = stream.current();
            if (DRL_KEYWORDS.includes(word)) {
                return 'keyword';
            }
            // @annotation
            if (word.startsWith('@')) {
                return 'attribute';
            }
            return 'variable';
        }

        stream.next();
        return null;
    },
    languageData: {
        commentTokens: { line: '//', block: ['/*', '*/'] },
    },
};
