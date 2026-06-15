/**
 * V5.78.3 — DRL 4 Monarch tokenizer (Monaco language services)。
 *
 * <p>Monarch 是 Monaco 的声明式 tokenizer(类似 TextMate grammar 的简化版,
 * 用 JSON 描述 token 规则)。本文件提供 DRL 关键字 / 注释 / 字符串 /
 * 数字 / 标识符的高亮,跟 {@code DrlLexer.g4} 关键字段对齐。
 *
 * <p>语法高亮是 Monaco 最小可用;IDE features(autocomplete / hover /
 * diagnostics)由 {@link ./DrlMonaco.tsx} 通过 Monaco provider API
 * 调 console-app 后端的 {@code /api/ide/*} 端点,不在 Monarch 里。
 *
 * <p>ref: https://microsoft.github.io/monaco-editor/monarch.html
 *
 * @since 5.78
 */
import type { languages } from 'monaco-editor';

export const DRL_LANGUAGE_ID = 'drl';

export const DRL_MONARCH_LANGUAGE: languages.IMonarchLanguage = {
    defaultToken: '',
    tokenPostfix: '.drl',

    // 关键字(跟 DrlLexer.g4 段顺序对齐;新增关键字须同步两处)
    keywords: [
        'package', 'dialect', 'import',
        'rule', 'query', 'function', 'declare',
        'when', 'then', 'end', 'return',
        'extends',
        'not', 'exists', 'eval', 'from', 'collect', 'accumulate',
        'init', 'action', 'reverse', 'result',
        'true', 'false', 'null',
    ],

    // rule attributes(顶层 [] 槽位)
    ruleAttributes: [
        'salience', 'agenda-group', 'activation-group', 'ruleflow-group',
        'auto-focus', 'no-loop', 'lock-on-active', 'enabled',
        'date-effective', 'date-expires', 'timer',
    ],

    // accumulate 内置函数
    accumulateFuncs: ['count', 'sum', 'avg', 'min', 'max'],

    operators: [
        '==', '!=', '>', '>=', '<', '<=',
        '&&', '||', '!', '?:', ':=',
    ],

    symbols: /[=><!~?:&|+\-*/^%]+/,

    tokenizer: {
        root: [
            // 标识符和关键字
            [
                /[a-zA-Z_][a-zA-Z0-9_-]*/,
                {
                    cases: {
                        '@keywords': 'keyword',
                        '@ruleAttributes': 'keyword.decl',
                        '@accumulateFuncs': 'keyword.accumulate',
                        '@default': 'identifier',
                    },
                },
            ],
            // 大写开头的类名 — DRL pattern Type(Applicant 之类)
            [/[A-Z][a-zA-Z0-9_]*/, 'type.identifier'],

            // 注释
            [/\/\/.*$/, 'comment'],
            [/\/\*/, 'comment', '@comment'],

            // 字符串
            [/"([^"\\]|\\.)*$/, 'string.invalid'],   // 未闭合字符串
            [/"/, { token: 'string.quote', bracket: '@open', next: '@string_double' }],

            // 数字
            [/\d+\.\d+([eE][-+]?\d+)?/, 'number.float'],
            [/\d+/, 'number'],

            // 标点
            [/[{}()[\]]/, '@brackets'],
            [/[<>](?!@symbols)/, '@brackets'],
            [/@symbols/, {
                cases: {
                    '@operators': 'operator',
                    '@default': '',
                },
            }],

            // 空白
            [/[ \t\r\n]+/, ''],
        ],

        comment: [
            [/[^/*]+/, 'comment'],
            [/\*\//, 'comment', '@pop'],
            [/[/*]/, 'comment'],
        ],

        string_double: [
            [/[^\\"]+/, 'string'],
            [/\\./, 'string.escape'],
            [/"/, { token: 'string.quote', bracket: '@close', next: '@pop' }],
        ],
    },
};
