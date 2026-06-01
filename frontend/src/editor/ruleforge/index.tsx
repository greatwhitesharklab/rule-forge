import '../../bootbox.js';
import '../context.standalone.css';
import './ruleset.css';
import '../../css/iconfont.css';
import '../../css/tailwind-base.css';
import '../uuid.js';
import '../common/contextMenu.js';
import '../common/URule.js';
import '../common/Context.js';
import '../../Remark.js';
import './RuleFactory.js';
import './RuleProperty.js';
import '../common/ComparisonOperator.js';
import '../common/ComplexArithmetic.js';
import '../common/VariableValue.js';
// @ts-ignore - side-effect import
import '../common/ResourceListDialog.js';
// @ts-ignore - side-effect import
import '../common/ResourceVersionDialog.js';
import '../common/ConstantValue.js';
import './ConfigActionDialog.js';
import './ConfigConstantDialog.js';
import './ConfigParameterDialog.js';
import './ConfigVariableDialog.js';
import './ActionType.js';
import './SimpleArithmetic.js';
import './PrintAction.js';
import './AssignmentAction.js';
import './Join.js';
import './NamedJoin.js';
import './NamedCondition.js';
import './Connection.js';
import '../common/InputType.js';
import '../common/NextType.js';
import '../common/Paren.js';
import '../common/MethodParameter.js';
import '../common/MethodAction.js';
import '../common/ParameterValue.js';
import '../common/MethodValue.js';
import '../common/FunctionProperty.js';
import '../common/FunctionParameter.js';
import '../common/FunctionValue.js';
import '../common/SimpleValue.js';
import './NamedReferenceValue.js';
import './Condition.js';
import './Rule.js';
import './LoopRule.js';

import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../../components/dialog/component/QuickTestDialog.jsx';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.tsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.tsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import EditorToolbar from '../../components/editor-toolbar/EditorToolbar.jsx';
import ReferenceDialog from '../../reference/ReferenceDialog.jsx';
import * as refEvent from '../../reference/event.js';
import * as componentEvent from '../../components/componentEvent.js';
import React from 'react';
import { createRoot } from 'react-dom/client';
import { getParameter, buildProjectNameFromFile, loadEditorData, handleResponseError } from '../../Utils.js';
import { save, saveNewVersion, formPost } from '../../api/client.js';
import { RuleFactory } from './RuleFactory.js';

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    if (!file || file.length < 1) {
        window.bootbox.alert('当前编辑器未指定具体规则文件！');
        return;
    }

    window._project = buildProjectNameFromFile(file);
    window.refEvent = refEvent;

    const factory = new RuleFactory(document.getElementById('container')!);
    factory.setFile(file);

    let toolbarApi: any = null;

    function handleSave(isNewVersion: boolean): void {
        let xml: string;
        try {
            xml = factory.toXml();
        } catch (error: unknown) {
            window.bootbox.alert(error as string);
            return;
        }
        xml = encodeURIComponent(xml);
        const url = window._server + '/common/saveFile';
        if (isNewVersion) {
            saveNewVersion(url, { file: file, content: xml }).then(function () {
                toolbarApi.clearDirty();
                window.bootbox.alert('保存成功!');
            }).catch(function () {});
        } else {
            const postData: Record<string, string> = { content: xml, file: file, newVersion: String(isNewVersion) };
            save(url, postData).then(function () {
                toolbarApi.clearDirty();
                window.bootbox.alert('保存成功!');
            });
        }
    }

    function addRule(): void {
        const ruleKey = prompt('规则编号', '');
        if (ruleKey == null || ruleKey === '') {
            const rule = factory.addRule();
            rule.initTopJoin();
        } else {
            const projectName = file.split('/')[1];
            formPost('/common/findRuleByKey', { ruleKey: ruleKey, projectName: projectName }).then(function (res: any[]) {
                if (res != null && res.length > 0) {
                    factory.addRule(res[0]);
                } else {
                    const rule = factory.addRule();
                    rule.initTopJoin();
                }
            }).catch(function () {});
        }
    }

    const decodedFile = decodeURIComponent(file);

    createRoot(document.getElementById('toolbarContainer')!).render(
        <EditorToolbar
            onSave={handleSave}
            onReady={(api: any) => { toolbarApi = api; }}
            extraButtons={[
                <button key="addRule" type="button" className="btn btn-default btn-sm"
                        onClick={addRule}>
                    <i className="glyphicon glyphicon-plus-sign"/> 添加规则
                </button>,
                <button key="addLoopRule" type="button" className="btn btn-default btn-sm"
                        onClick={() => { const rule = factory.addLoopRule(); rule.initTopJoin(); }}>
                    <i className="glyphicon glyphicon-plus"/> 添加循环规则
                </button>,
                <button key="test" type="button" className="btn btn-success"
                        onClick={() => componentEvent.eventEmitter.emit(componentEvent.OPEN_QUICK_TEST_DIALOG, { project: window._project, file: decodedFile, type: 'ruleLib' })}>
                    <i className="glyphicon glyphicon-flash"/> 快速测试
                </button>,
                <button key="reference" type="button" className="btn btn-info"
                        onClick={() => {
                            const title = '规则集"' + decodedFile + '"';
                            if (window.refEvent) {
                                (window.refEvent as any).eventEmitter.emit((window.refEvent as any).OPEN_REFERENCE_DIALOG, { path: decodedFile }, title);
                            }
                        }}>
                    <i className="rf rf-link"/> 查看引用
                </button>
            ]}
        />
    );

    createRoot(document.getElementById('dialogContainer')!).render(
        <div>
            <KnowledgeTreeDialog/>
            <ConfigLibraryDialog/>
            <ReferenceDialog/>
            <QuickTestDialog/>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
        </div>
    );

    // Load ruleset data
    loadEditorData(file).then(function (editorData: any) {
        factory.loadData(editorData);
        toolbarApi.clearDirty();
    }).catch(function (response: any) {
        handleResponseError(response, '加载文件失败，服务端错误：');
    });
});
