import '../bootbox.js';
import '../css/iconfont.css';
import '../css/tailwind-base.css';
import React, {createElement} from 'react';
import {createRoot} from 'react-dom/client';
import FlowEditor from './FlowEditor.jsx';
import KnowledgeTreeDialog from '../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../components/dialog/component/QuickTestDialog.jsx';
import {buildProjectNameFromFile, getParameter, handleResponseError} from '../Utils.js';
import {save, saveNewVersion, formPost} from '../api/client.js';
import * as event from '../components/componentEvent.js';
import * as componentEvent from '../components/componentEvent.js';

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    if (!file || file.length < 1) {
        window.bootbox.alert('当前编辑器未指定具体文件！');
        return;
    }
    window._project = buildProjectNameFromFile(file);
    let editorRef: any = null;
    const decodedFile = decodeURIComponent(file);

    function openImportDialog(fileType: string) {
        componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            fileType: fileType,
            callback: function (f: string, version: string) {
                let path = 'jcr:' + f;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                if (!editorRef) return;
                editorRef.addImport(fileType, path);
            }
        });
    }

    function saveFlow(newVersion: boolean) {
        if (!editorRef) return;
        editorRef.saveXML().then(function (xml: string) {
            if (!xml) return;
            event.eventEmitter.emit(event.SHOW_LOADING, '数据保存中');
            const postData: Record<string, string> = {content: encodeURIComponent(xml), file: file, newVersion: String(newVersion)};
            const url = window._server + '/common/saveFile';
            if (newVersion) {
                saveNewVersion(url, {file, content: encodeURIComponent(xml)}).then(function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                    window.bootbox.alert('保存成功!');
                }).catch(function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                });
            } else {
                save(url, postData).then(function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                    window.bootbox.alert('保存成功!');
                });
            }
        });
    }

    // Render toolbar
    const toolbarRoot = document.getElementById('toolbarContainer');
    if (toolbarRoot) {
        createRoot(toolbarRoot).render(
            createElement('div', {className: 'toolbar'},
                createElement('button', {
                    className: 'btn btn-ghost btn-sm',
                    onClick: function () { saveFlow(false); }
                }, createElement('i', {className: 'rf rf-save'}), ' 保存'),
                ' ',
                createElement('button', {
                    className: 'btn btn-ghost btn-sm',
                    onClick: function () { saveFlow(true); }
                }, createElement('i', {className: 'rf rf-savenewversion'}), ' 生成版本'),
                ' ',
                createElement('button', {
                    className: 'btn btn-primary btn-sm',
                    onClick: function () {
                        event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {
                            project: window._project, file: decodedFile
                        });
                    }
                }, createElement('i', {className: 'glyphicon glyphicon-flash'}), ' 快速测试'),
                ' | ',
                createElement('button', {
                    className: 'btn btn-ghost btn-sm',
                    onClick: function () { openImportDialog('VariableLibrary'); }
                }, createElement('i', {className: 'rf rf-variable'}), ' 变量库'),
                createElement('button', {
                    className: 'btn btn-ghost btn-sm',
                    onClick: function () { openImportDialog('ConstantLibrary'); }
                }, createElement('i', {className: 'rf rf-constant'}), ' 常量库'),
                createElement('button', {
                    className: 'btn btn-ghost btn-sm',
                    onClick: function () { openImportDialog('ActionLibrary'); }
                }, createElement('i', {className: 'rf rf-action'}), ' 动作库'),
                createElement('button', {
                    className: 'btn btn-ghost btn-sm',
                    onClick: function () { openImportDialog('ParameterLibrary'); }
                }, createElement('i', {className: 'rf rf-parameter'}), ' 参数库'),
                ' | ',
                createElement('button', {
                    className: 'btn btn-success btn-sm',
                    onClick: function () {
                        if (!editorRef) return;
                        editorRef.saveXML().then(function (xml: string) {
                            if (!xml) return;
                            formPost('/flow/deploy', {file: file}).then(function (data: any) {
                                window.bootbox.alert('部署成功! Deployment ID: ' + (data.deploymentId || ''));
                            }).catch(function (response: any) {
                                handleResponseError(response, '部署失败：');
                            });
                        });
                    }
                }, createElement('i', {className: 'glyphicon glyphicon-upload'}), ' 部署')
            )
        );
    }

    // Render editor
    createRoot(document.getElementById('container')!).render(
        createElement(FlowEditor, {
            onReady: function (ref: any) { editorRef = ref; }
        })
    );

    // Render dialogs
    createRoot(document.getElementById('dialogContainer')!).render(
        createElement('div', null,
            createElement(KnowledgeTreeDialog),
            createElement(QuickTestDialog)
        )
    );

    // Load BPMN XML
    fetch(window._server + '/flow/load?file=' + encodeURIComponent(file)).then(function (response: any) {
        if (!response.ok) throw response;
        return response.text();
    }).then(function (xml: string) {
        if (editorRef) {
            editorRef.loadXML(xml);
        }
    }).catch(function (response: any) {
        if (response && response.status === 404) {
            // New file - editor already shows empty diagram
        } else {
            handleResponseError(response, '加载决策流失败：');
        }
    });
});
