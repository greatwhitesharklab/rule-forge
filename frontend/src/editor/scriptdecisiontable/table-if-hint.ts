/**
 * CodeMirror hint addon for the "if" dialect auto-completion.
 *
 * Previously `table-if-hint.js`.
 */

import CodeMirror from 'codemirror';

const Pos = (CodeMirror as any).Pos;

function forEach(arr: string[], f: (s: string) => void): void {
    for (let i = 0, e = arr.length; i < e; ++i) {
        f(arr[i]);
    }
}

function arrayContains(arr: string[], item: string): boolean {
    return arr.indexOf(item) !== -1;
}

function getMode(editor: any): any {
    const cur = editor.getCursor();
    return editor.getModeAt({ line: cur.line, ch: cur.ch });
}

function scriptHint(editor: any, keywords: string[] | undefined, getToken: (e: any, cur: any) => any, options: any): any {
    const cur = editor.getCursor(),
        token = getToken(editor, cur),
        comment = /\b(?:string|comment)\b/,
        property = /^[\w$_一-龥]*$/;
    if (comment.test(token.type)) return;
    token.state = (CodeMirror as any).innerMode(editor.getMode(), token.state).state;
    if (token.type === 'property') {
        token.start = cur.ch - token.string.length + 1;
        token.end = cur.ch + token.string.length - 1;
        token.string = token.string.replace('.', '');
    } else if (!property.test(token.string)) {
        token.start = cur.ch;
        token.end = cur.ch;
        token.string = '';
    } else if (token.end > cur.ch) {
        token.end = cur.ch;
        token.string = token.string.slice(0, cur.ch - token.start);
    }
    let tprop = token;
    let context: any[] = [];
    while (true) {
        tprop = getToken(editor, Pos(cur.line, tprop.start));
        if (tprop.type !== 'property') break;
        tprop = getToken(editor, Pos(cur.line, tprop.start));
        context.push(tprop);
    }
    return {
        list: getCompletions(token, context, keywords, options),
        from: Pos(cur.line, token.start),
        to: Pos(cur.line, token.end)
    };
}

function ruleforgeHint(editor: any, options: any): any {
    const mode = getMode(editor);
    const block = mode.name;
    let keywords: string[] | undefined;
    if (block === 'if') {
        keywords = ifKeywords;
    } else if (block === 'then') {
        keywords = thenKeywords;
    } else if (block === 'print') {
        keywords = printKeywords;
    }
    return scriptHint(editor, keywords,
        function (e: any, cur: any) { return e.getTokenAt(cur); },
        options);
}

(CodeMirror as any).registerHelper('hint', 'if', ruleforgeHint);

const ifKeywords = ('参数 大于 大于等于 小于 小于等于 等于 不等于 结束于 不结束于 开始于 不开始于 在集合中 不在集合中 匹配 不匹配 忽略大小写等于 忽略大小写不等').split(' ');
const thenKeywords = ('参数').split(' ');
const printKeywords = ('参数').split(' ');

function getCompletions(token: any, context: any[], keywords: string[] | undefined, options: any): string[] {
    const found: string[] = [];
    const start = token.string;

    function maybeAdd(str: string): void {
        str = str || '';
        if (str.toUpperCase().lastIndexOf(start.replace(/\s*/g, '').toUpperCase(), 0) === 0 && !arrayContains(found, str)) {
            found.push(str);
        }
    }

    function gatherCompletions(base: string): void {
        const actionLibraries = (window as any)._ruleforgeEditorActionLibraries || [],
            parameter = (window as any)._ruleforgeEditorParameterLibraries || [],
            variableCategories = (window as any)._ruleforgeEditorVariableLibraries || [],
            constantCategories = (window as any)._ruleforgeEditorConstantLibraries || [];
        actionLibraries.forEach(function (library: any): void {
            const springBeans = library.springBeans || [];
            for (let j = 0; j < springBeans.length; j++) {
                const springBean = springBeans[j],
                    methods = springBean.methods || [];
                if (springBean.name.toUpperCase().lastIndexOf(base.toUpperCase(), 0) !== 0) continue;
                for (let k = 0; k < methods.length; k++) {
                    const method = methods[k],
                        parameters = method.parameters || [];
                    let name = method.name + '(';
                    const ps: string[] = [];
                    for (let z = 0; z < parameters.length; z++) {
                        ps.push(parameters[z].name);
                    }
                    name += ps.join(',') + ')';
                    maybeAdd(name);
                }
            }
        });
        variableCategories.forEach(function (categories: any[]): void {
            for (let i = 0; i < categories.length; i++) {
                const variableCategory = categories[i],
                    variables = variableCategory.variables || [];
                if (variableCategory.name.toUpperCase().lastIndexOf(base.toUpperCase(), 0) !== 0) continue;
                for (let j = 0; j < variables.length; j++) {
                    const variable = variables[j];
                    maybeAdd(variable.label);
                }
            }
        });
        constantCategories.forEach(function (categories: any): void {
            categories = categories.categories;
            for (let i = 0; i < categories.length; i++) {
                const constantCategory = categories[i],
                    constants = constantCategory.constants || [];
                const name = '$' + constantCategory.label;
                if (name.toUpperCase().lastIndexOf(base.toUpperCase(), 0) !== 0) continue;
                for (let j = 0; j < constants.length; j++) {
                    maybeAdd(constants[j].label);
                }
            }
        });
        parameter.forEach(function (ps: any[]): void {
            if ('参数' === base) {
                for (let i = 0; i < ps.length; i++) {
                    maybeAdd(ps[i].label);
                }
            }
        });
    }

    if (context && context.length) {
        const obj = context.pop();
        let base: string;
        if (obj.type && (obj.type.indexOf('variable') === 0 || obj.type.indexOf('atom-2') === 0)) {
            base = obj.string;
            if (base) {
                if (base === '参数') {
                    gatherCompletions('参数');
                } else {
                    gatherCompletions(base);
                }
            }
        }
    } else {
        const actionLibraries = (window as any)._ruleforgeEditorActionLibraries || [],
            variableCategories = (window as any)._ruleforgeEditorVariableLibraries || [],
            constantCategories = (window as any)._ruleforgeEditorConstantLibraries || [];
        actionLibraries.forEach(function (library: any): void {
            const springBeans = library.springBeans;
            for (let j = 0; j < springBeans.length; j++) {
                maybeAdd(springBeans[j].name);
            }
        });
        variableCategories.forEach(function (categories: any[]): void {
            for (let i = 0; i < categories.length; i++) {
                maybeAdd(categories[i].name);
            }
        });
        constantCategories.forEach(function (categories: any): void {
            categories = categories.categories;
            for (let i = 0; i < categories.length; i++) {
                maybeAdd('$' + categories[i].label);
            }
        });
        if (keywords) {
            forEach(keywords, maybeAdd);
        }
    }
    return found;
}
