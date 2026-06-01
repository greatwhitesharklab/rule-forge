/**
 * CodeMirror simple mode for the "if" (condition) dialect.
 *
 * Previously `table_if_mode.js`.
 */

import CodeMirror from 'codemirror';

(CodeMirror as any).defineSimpleMode('if', {
    start: [
        { regex: /"(?:[^\\]|\\.)*?"/, token: 'string' },
        { regex: /(true|false|null|and|or)\b/, token: 'atom' },
        { regex: /\s+(或者|或|并且)\s+/, token: 'atom' },
        { regex: /参数/, token: 'atom-2' },
        { regex: /(out|eval|all|exist|collect)\b/, token: 'atom-3' },
        { regex: /\.([\w$_一-龥][\w$_一-龥\d]*)*/, token: 'property' },
        { regex: /0x[a-f\d]+|[-+]?(?:\.\d+|\d+\.?\d*)(?:e[-+]?\d+)?/i, token: 'number' },
        { regex: /\/\/.*/, token: 'comment' },
        { regex: /\/(?:[^\\]|\\.)*?\//, token: 'comment' },
        { regex: /\/\*/, token: 'comment', next: 'comment' },
        { regex: /[-+\/*=<>!]+/, token: 'operator' },
        { regex: /(大于|大于等于|小于|小于等于|等于|不等于|结束于|不结束于|开始于|不开始于|在集合中|不在集合中|匹配|不匹配|忽略大小写等于|忽略大小写不等)\s+/, token: 'operator' },
        { regex: /[\{\[\(]/, indent: true },
        { regex: /[\}\]\)]/, dedent: true },
        { regex: /[\w$_一-龥][\w$_一-龥\d]*/, token: 'variable' },
        { regex: /<</, token: 'meta', mode: { spec: 'xml', end: />>/ } }
    ],
    comment: [
        { regex: /.*?\*\//, token: 'comment', next: 'start' },
        { regex: /.*/, token: 'comment' }
    ],
    meta: {
        dontIndentStates: ['comment'],
        lineComment: '//'
    }
});
