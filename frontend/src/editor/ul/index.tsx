import '../../bootbox.js';
import '../../../node_modules/codemirror/lib/codemirror.css';
import '../../../node_modules/codemirror/addon/hint/show-hint.css';
import '../../../node_modules/codemirror/addon/lint/lint.css';
import './ul.css';
import '../../css/tailwind-base.css';
import React from 'react';
import {createRoot} from 'react-dom/client';
/* bootbox is a global */
import CodeMirror from 'codemirror';
import '../../../node_modules/codemirror/addon/mode/simple.js';
import '../../../node_modules/codemirror/addon/hint/show-hint.js';
import '../../../node_modules/codemirror/addon/lint/lint.js';
import './ruleforge_mode.js';
import './ruleforge-hint.js';
import './ruleforgemixed.js';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import QuickTestDialog from '../../components/dialog/component/QuickTestDialog.jsx';
import EditorToolbar from '../../components/editor-toolbar/EditorToolbar.jsx';
import {buildProjectNameFromFile, getParameter, handleResponseError} from '../../Utils.js';
import {save, saveNewVersion, formPost} from '../../api/client.js';
import * as event from '../../components/componentEvent.js';
import * as componentEvent from '../../components/componentEvent.js';

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    CodeMirror.commands.autocomplete = function (cm: CodeMirrorEditor) {
        cm.showHint({hint: (CodeMirror as any).hint.ruleforge});
    };
    const codeEditor = document.getElementById('codeEditor') as HTMLTextAreaElement;
    (window as any).codeMirror = CodeMirror.fromTextArea(codeEditor, {
        lineNumbers: true,
        mode: 'rulemixed',
        extraKeys: {'Alt-/': 'autocomplete'},
        gutters: ['CodeMirror-linenumbers', 'CodeMirror-lint-markers'],
        lint: {
            getAnnotations: buildScriptLintFunction('Script'),
            async: true
        }
    });
    const codeMirror = (window as any).codeMirror as CodeMirrorEditor;
    codeMirror.on('change', function (cm: CodeMirrorEditor, e: any) {
        const value = cm.getValue();
        if (e.text === '.') {
            CodeMirror.commands.autocomplete(codeMirror);
        }
    });

    const height = document.documentElement.scrollHeight - 60;
    codeMirror.setSize('100%', height);
    window._dirty = false;

    if (!file || file.length < 1) {
        alert('当前编辑器未指定具体文件！');
        return;
    }

    let toolbarApi: any = null;
    const decodedFile = decodeURIComponent(file);

    function saveUL(newVersion: boolean) {
        if (!newVersion && window._dirty === false) return;
        let content = codeMirror.getValue();
        content = encodeURIComponent(content);
        const postData = {content, file};
        const url = window._server + '/common/saveFile';
        if (newVersion) {
            saveNewVersion(url, { file, content }).then(function () {
                toolbarApi.clearDirty();
                window.bootbox.alert('保存成功!');
            }).catch(function () {});
        } else {
            save(url, postData as Record<string, string>).then(function () {
                toolbarApi.clearDirty();
            });
        }
    }

    function openImportDialog(fileType: string) {
        componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            fileType: fileType,
            callback: function (f: string, version: string) {
                let path = 'jcr:' + f;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                codeMirror.replaceSelection('import' + fileType + ' "' + path + '";');
                loadResLib();
            }
        });
    }

    function loadResLib() {
        const content = codeMirror.getValue();
        if (!content || content.length < 10) {
            window.bootbox.alert('请先输入脚本.');
            return;
        }
        formPost('/uleditor/loadULLibs', {content: content}).then(function (data: any) {
            codeMirror._library = data;
        }).catch(function (response: any) {
            handleResponseError(response, '资源库加载失败：');
        });
    }

    createRoot(document.getElementById('toolbarContainer')!).render(
        <EditorToolbar
            onSave={saveUL}
            onReady={(api: any) => { toolbarApi = api; }}
            showVariable={false}
            showConstant={false}
            showAction={false}
            showParameter={false}
            extraButtons={[
                <button key="importVar" type="button" className="btn btn-default"
                        onClick={() => openImportDialog('VariableLibrary')}>
                    <i className="rf rf-variable"></i> 变量库
                </button>,
                <button key="importConst" type="button" className="btn btn-default"
                        onClick={() => openImportDialog('ConstantLibrary')}>
                    <i className="rf rf-constant"></i> 常量库
                </button>,
                <button key="importAction" type="button" className="btn btn-default"
                        onClick={() => openImportDialog('ActionLibrary')}>
                    <i className="rf rf-action"></i> 动作库
                </button>,
                <button key="importParam" type="button" className="btn btn-default"
                        onClick={() => openImportDialog('ParameterLibrary')}>
                    <i className="rf rf-parameter"></i> 参数库
                </button>,
                <button key="test" type="button" className="btn btn-success"
                        onClick={() => event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile})}>
                    <i className="glyphicon glyphicon-flash"/> 快速测试
                </button>
            ]}
        />
    );

    createRoot(document.getElementById('dialogContainer')!).render(
        <div>
            <KnowledgeTreeDialog/>
            <ConfigLibraryDialog/>
            <QuickTestDialog/>
        </div>
    );

    // Load UL data
    const url = window._server + '/uleditor/loadUL';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({file}).toString()
    }).then(function (response) {
        if (!response.ok) throw response;
        return response.text();
    }).then(function (data: string) {
        codeMirror.setValue(data);
        codeMirror.on('change', function () {
            window._setDirty();
        });
        loadResLib();
    }).catch(function (response: any) {
        handleResponseError(response, '文件加载失败：');
    });
});

function buildScriptLintFunction(type: string) {
    return function (text: string, updateLinting: (editor: any, annotations: any[]) => void, _options: any, editor: any) {
        if (text === '') {
            updateLinting(editor, []);
            return;
        }
        formPost('/common/scriptValidation', {type, content: text}).then(function (result: any[]) {
            if (result) {
                for (const item of result) {
                    item.from = {line: item.line - 1};
                    item.to = {line: item.line - 1};
                }
                updateLinting(editor, result);
            } else {
                updateLinting(editor, []);
            }
        }).catch(function (response: any) {
            handleResponseError(response, '语法检查操作失败：');
        });
    };
}
