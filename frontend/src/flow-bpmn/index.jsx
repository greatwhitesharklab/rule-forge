import '../bootbox.js';
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../css/iconfont.css';
import {createRoot} from 'react-dom/client';
import {createElement} from 'react';
import FlowEditor from './FlowEditor.jsx';
import KnowledgeTreeDialog from '../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../components/dialog/component/QuickTestDialog.jsx';
import {buildProjectNameFromFile, getParameter, ajaxSave, saveNewVersion, handleResponseError} from '../Utils.js';
import * as event from '../components/componentEvent.js';

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    if (!file || file.length < 1) {
        window.bootbox.alert("当前编辑器未指定具体文件！");
        return;
    }
    window._project = buildProjectNameFromFile(file);
    var editorRef = null;
    var decodedFile = decodeURIComponent(file);

    function saveFlow(newVersion) {
        if (!editorRef) return;
        editorRef.saveXML().then(function (xml) {
            if (!xml) return;
            event.eventEmitter.emit(event.SHOW_LOADING, "数据保存中");
            var postData = {content: encodeURIComponent(xml), file: file, newVersion: newVersion};
            var url = window._server + '/common/saveFile';
            if (newVersion) {
                saveNewVersion(url, postData, function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                    window.bootbox.alert('保存成功!');
                });
            } else {
                ajaxSave(url, postData, function () {
                    event.eventEmitter.emit(event.HIDE_LOADING);
                    window.bootbox.alert('保存成功!');
                });
            }
        });
    }

    // Render toolbar
    var toolbarRoot = document.getElementById('toolbarContainer');
    if (toolbarRoot) {
        createRoot(toolbarRoot).render(
            createElement('div', {style: {padding: '5px 10px'}},
                createElement('button', {
                    className: 'btn btn-default btn-sm',
                    onClick: function () { saveFlow(false); }
                }, createElement('i', {className: 'rf rf-save'}), ' 保存'),
                ' ',
                createElement('button', {
                    className: 'btn btn-default btn-sm',
                    onClick: function () { saveFlow(true); }
                }, createElement('i', {className: 'rf rf-savenewversion'}), ' 生成版本'),
                ' ',
                createElement('button', {
                    className: 'btn btn-info btn-sm',
                    onClick: function () {
                        event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {
                            project: window._project, file: decodedFile
                        });
                    }
                }, createElement('i', {className: 'glyphicon glyphicon-flash'}), ' 快速测试')
            )
        );
    }

    // Render editor
    createRoot(document.getElementById('container')).render(
        createElement(FlowEditor, {
            onReady: function (ref) { editorRef = ref; }
        })
    );

    // Render dialogs
    createRoot(document.getElementById('dialogContainer')).render(
        createElement('div', null,
            createElement(KnowledgeTreeDialog),
            createElement(QuickTestDialog)
        )
    );

    // Load BPMN XML
    fetch(window._server + '/flow/load', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({file: file}).toString()
    }).then(function (response) {
        if (!response.ok) throw response;
        return response.text();
    }).then(function (xml) {
        if (editorRef) {
            editorRef.loadXML(xml);
        }
    }).catch(function (response) {
        // If no BPMN file exists yet, create a new diagram
        if (response && response.status === 404) {
            // New file - editor already shows empty diagram
        } else {
            handleResponseError(response, '加载决策流失败：');
        }
    });
});
