/**
 * CodeMirror hint addon for RuleForge DSL autocomplete.
 */
import CodeMirror from 'codemirror';

const Pos = CodeMirror.Pos;

interface HintToken {
    start: number;
    end: number;
    string: string;
    type?: string;
    state: unknown;
}

function forEach(arr: string[], f: (s: string) => void): void {
    for (let i = 0, e = arr.length; i < e; ++i) {
        f(arr[i]);
    }
}

function arrayContains(arr: string[], item: string): boolean {
    return arr.indexOf(item) !== -1;
}

function getMode(editor: CodeMirrorEditor): unknown {
    const cur = editor.getCursor();
    return editor.getModeAt({line: cur.line, ch: cur.ch});
}

function getBlock(): string {
    let block: string | null = null;
    let line = (window as any).codeMirror.getCursor().line;
    while (!block && line >= 0) {
        block = (window as any).codeMirror.getStateAfter(line).block;
        line--;
    }
    if (line < 0) {
        block = 'import';
    }
    return block;
}

function scriptHint(editor: CodeMirrorEditor, keywords: string[], getToken: (e: CodeMirrorEditor, cur: CodeMirror.Position) => any, options: any): any {
    const cur = editor.getCursor();
    let token: any = getToken(editor, cur);
    const comment = /\b(?:string|comment)\b/;
    const property = /^[\w$_一-龥]*$/;
    if (comment.test(token.type)) return;
    token.state = CodeMirror.innerMode(editor.getMode(), token.state).state;
    if (token.type === 'property') {
        token = {
            start: cur.ch - token.string.length + 1,
            end: cur.ch + token.string.length - 1,
            string: token.string.replace('.', ''),
            state: token.state
        };
    } else if (!property.test(token.string)) {
        token = {
            start: cur.ch,
            end: cur.ch,
            string: '',
            state: token.state
        };
    } else if (token.end > cur.ch) {
        token.end = cur.ch;
        token.string = token.string.slice(0, cur.ch - token.start);
    }
    let tprop = token;
    let context: any[] | undefined;
    while (true) {
        tprop = getToken(editor, Pos(cur.line, tprop.start));
        if (tprop.type !== 'property') break;
        tprop = getToken(editor, Pos(cur.line, tprop.start));
        if (!context) {
            context = [];
        }
        context.push(tprop);
    }
    return {
        list: getCompletions(token, context, keywords, options),
        from: Pos(cur.line, token.start),
        to: Pos(cur.line, token.end)
    };
}

function ruleforgeHint(editor: CodeMirrorEditor, options: any): any {
    let keywords = javaKeywords;
    const mode = getMode(editor) as any;
    if (mode.name === 'ruleforge') {
        const block = getBlock();
        if (block === 'import') {
            keywords = importKeywords;
        } else if (block === 'rule') {
            keywords = ruleKeywords;
        } else if (block === 'if') {
            keywords = ifKeywords;
        } else if (block === 'then') {
            keywords = thenKeywords;
        } else if (block === 'end') {
            keywords = endKeywords;
        }
    }
    return scriptHint(editor, keywords,
        (e: CodeMirrorEditor, cur: CodeMirror.Position) => e.getTokenAt(cur),
        options);
}

CodeMirror.registerHelper('hint', 'ruleforge', ruleforgeHint);

const importKeywords = ('importVariableLibrary importConstrantLibrary importActionLibrary import function').split(' ');
const ruleKeywords = ('salience effective-date expires-date enabled debug activation-group agenda-group auto-focus ruleflow-group').split(' ');
const ifKeywords = ('参数 > >= < <= == != Endwith NotEndwith Startwith NotStartwith In NotIn Match NotMatch EqualsIgnoreCase NotEqualsIgnoreCase Contain NotContain eval()').split(' ');
const thenKeywords = ('参数 out').split(' ');
const endKeywords = ('function').split(' ');
const javaKeywords = ('abstract assert boolean break byte case catch char class const continue default ' +
    'do double else enum extends final finally float for goto if implements import ' +
    'instanceof int interface long native new package private protected public ' +
    'return short static strictfp super switch synchronized this throw throws transient ' +
    'try void volatile while').split(' ');

function getCompletions(token: HintToken, context: any[] | undefined, keywords: string[], options: any): string[] {
    const found: string[] = [];
    const start = token.string;

    function maybeAdd(str: string): void {
        if (str.toUpperCase().lastIndexOf(start.replace(/\s*/g, '').toUpperCase(), 0) === 0 && !arrayContains(found, str)) found.push(str);
    }

    function gatherCompletions(base: string): void {
        const block = getBlock();
        const cm = (window as any).codeMirror;
        if (!cm._library || (block !== 'if' && block !== 'then')) return;
        const library = cm._library;
        const actionLibraries = library.actionLibraries || [];
        const variableCategories = library.variableCategories || [];
        const constantCategories = library.constantCategories || [];

        for (let i = 0; i < actionLibraries.length; i++) {
            const actionLibrary = actionLibraries[i];
            const springBeans = actionLibrary.springBeans || [];
            for (let j = 0; j < springBeans.length; j++) {
                const springBean = springBeans[j];
                const methods = springBean.methods || [];
                if (springBean.name.toUpperCase().lastIndexOf(base.toUpperCase(), 0) !== 0) continue;
                for (let k = 0; k < methods.length; k++) {
                    const method = methods[k];
                    const parameters = method.parameters || [];
                    let name = method.name + '(';
                    const ps: string[] = [];
                    for (let z = 0; z < parameters.length; z++) {
                        ps.push(parameters[z].name);
                    }
                    name += ps.join(',') + ')';
                    maybeAdd(name);
                }
            }
        }

        for (let i = 0; i < variableCategories.length; i++) {
            const variableCategorie = variableCategories[i];
            const variables = variableCategorie.variables || [];
            if (variableCategorie.name.toUpperCase().lastIndexOf(base.toUpperCase(), 0) !== 0) continue;
            for (let j = 0; j < variables.length; j++) {
                maybeAdd(variables[j].label);
            }
        }

        for (let i = 0; i < constantCategories.length; i++) {
            const constantCategory = constantCategories[i];
            const constants = constantCategory.constants || [];
            const name = '$' + constantCategory.label;
            if (name.toUpperCase().lastIndexOf(base.toUpperCase(), 0) !== 0) continue;
            for (let j = 0; j < constants.length; j++) {
                maybeAdd(constants[j].label);
            }
        }
    }

    if (context && context.length) {
        const obj = context.pop();
        if (obj.type && (obj.type.indexOf('variable') === 0 || obj.type.indexOf('atom-2') === 0)) {
            const base = obj.string;
            if (base) {
                if (base === '参数') {
                    gatherCompletions('参数');
                } else {
                    gatherCompletions(base);
                }
            }
        }
    } else {
        const block = getBlock();
        const cm = (window as any).codeMirror;
        if (cm._library && (block === 'if' || block === 'then')) {
            const library = cm._library;
            const actionLibraries = library.actionLibraries;
            const variableCategories = library.variableCategories;
            const constantCategories = library.constantCategories;

            for (let i = 0; i < actionLibraries.length; i++) {
                const springBeans = actionLibraries[i].springBeans;
                for (let j = 0; j < springBeans.length; j++) {
                    maybeAdd(springBeans[j].name);
                }
            }

            for (let i = 0; i < variableCategories.length; i++) {
                const name = variableCategories[i].name;
                if (name !== '参数') {
                    maybeAdd(name);
                }
            }

            for (let i = 0; i < constantCategories.length; i++) {
                maybeAdd('$' + constantCategories[i].label);
            }
        }
        forEach(keywords, maybeAdd);
    }
    return found;
}
