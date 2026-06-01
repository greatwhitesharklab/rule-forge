/**
 * CodeMirror simple mode for the "then" (action) dialect.
 *
 * Previously `then_mode.js`.
 */

import CodeMirror from 'codemirror';

(CodeMirror as any).defineSimpleMode('then', {
    start: [
        { regex: /"(?:[^\\]|\\.)*?"/, token: 'string' },
        { regex: /(true|false|null)\b/, token: 'atom' },
        { regex: /参数/, token: 'atom-2' },
        { regex: /(out)\b/, token: 'atom-3' },
        { regex: /\.([\w$_一-龥][\w$_一-龥\d]*)*/, token: 'property' },
        { regex: /0x[a-f\d]+|[-+]?(?:\.\d+|\d+\.?\d*)(?:e[-+]?\d+)?/i, token: 'number' },
        { regex: /\/\/.*/, token: 'comment' },
        { regex: /\/(?:[^\\]|\\.)*?\//, token: 'comment' },
        { regex: /\/\*/, token: 'comment', next: 'comment' },
        { regex: /[-+\/*]+/, token: 'operator' },
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
