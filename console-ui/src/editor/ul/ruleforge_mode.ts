/**
 * CodeMirror simple mode definition for RuleForge DSL.
 */
import CodeMirror from 'codemirror';

CodeMirror.defineSimpleMode('ruleforge', {
    start: [
        {regex: /"(?:[^\\]|\\.)*?"/, token: 'string'},
        {regex: /(function)(\s+)([a-z$][\w$]*)/, token: ['keyword', null, 'variable-2']},
        {regex: /(rule|loopRule|loopTarget|loopStart|loopEnd|if|then|else|end)\b/, token: 'keyword'},
        {regex: /(规则|循环规则|循环对象|开始前动作|结束后动作|如果|那么|否则|结束)/, token: 'keyword'},
        {regex: /(true|false|null|and|or|importParameterLibrary|importVariableLibrary|importConstantLibrary|importActionLibrary|salience|loop|effective-date|expires-date|enabled|debug|activation-group|agenda-group|auto-focus)\b/, token: 'atom'},
        {regex: /\s+(或者|或|并且|且)\s+/, token: 'atom'},
        {regex: /参数/, token: 'atom-2'},
        {regex: /(out|eval|all|exist|collect|count)\b/, token: 'atom-3'},
        {regex: /\.([\w$_一-龥][\w$_一-龥\d]*)*/, token: 'property'},
        {regex: /0x[a-f\d]+|[-+]?(?:\.\d+|\d+\.?\d*)(?:e[-+]?\d+)?/i, token: 'number'},
        {regex: /\/\/.*/, token: 'comment'},
        {regex: /\/(?:[^\\]|\\.)*?\//, token: 'comment'},
        {regex: /\/\*/, token: 'comment', next: 'comment'},
        {regex: /[-+\/*=<>!]+/, token: 'operator'},
        {regex: /(Endwith|NotEndwith|Startwith|NotStartwith|In|NotIn|Match|NotMatch|'EqualsIgnoreCase|NotEqualsIgnoreCase)\b/, token: 'operator'},
        {regex: /[\{\[\(]/, indent: true},
        {regex: /[\}\]\)]/, dedent: true},
        {regex: /[\w$_一-龥][\w$_一-龥\d]*/, token: 'variable'},
        {regex: /<</, token: 'meta', mode: {spec: 'xml', end: />>/}}
    ],
    comment: [
        {regex: /.*?\*\//, token: 'comment', next: 'start'},
        {regex: /.*/, token: 'comment'}
    ],
    meta: {
        dontIndentStates: ['comment'],
        lineComment: '//'
    }
});
