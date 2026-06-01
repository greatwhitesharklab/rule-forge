import '../bootbox.js';
import ScoreCardTable from './ScoreCardTable';
import '../css/iconfont.css';
import './scorecard.css';
import '../editor/context.standalone.css';
import '../editor/ruleforge/ruleset.css';
import '../css/tailwind-base.css';
import React from 'react';
import {createRoot} from 'react-dom/client';
import '../Remark.js';
import '../editor/common/URule.js';
import '../editor/common/contextMenu.js';
import '../editor/uuid.js';
import '../editor/common/Context.js';
import '../editor/decisiontable/CellCondition.js';
import '../editor/decisiontable/Condition.js';
import '../editor/decisiontable/Join.js';
import '../editor/decisiontable/Connection.js';

import '../editor/common/ComparisonOperator.js';
import '../editor/common/ComplexArithmetic.js';
import '../editor/common/VariableValue.js';
import '../editor/common/ConstantValue.js';

import '../editor/ruleforge/SimpleArithmetic.js';
import '../editor/common/InputType.js';
import '../editor/common/NextType.js';
import '../editor/common/Paren.js';
import '../editor/common/MethodParameter.js';
import '../editor/common/MethodAction.js';
import '../editor/common/ParameterValue.js';
import '../editor/common/MethodValue.js';
import '../editor/common/FunctionProperty.js';
import '../editor/common/FunctionParameter.js';
import '../editor/common/FunctionValue.js';
import '../editor/common/SimpleValue.js';

import '../editor/ruleforge/ConfigActionDialog.js';
import '../editor/ruleforge/ConfigConstantDialog.js';
import '../editor/ruleforge/ConfigParameterDialog.js';
import '../editor/ruleforge/ConfigVariableDialog.js';
import '../editor/ruleforge/RuleProperty.js';
import * as event from '../components/componentEvent.js';
import QuickTestDialog from '../components/dialog/component/QuickTestDialog.jsx';
import ConfigLibraryDialog from '../components/dialog/component/ConfigLibraryDialog.jsx';
import EditorToolbar from '../components/editor-toolbar/EditorToolbar.jsx';
import {saveNewVersion} from "../api/client.js";

import {buildProjectNameFromFile, getParameter, loadEditorData} from '../Utils.js';
import {save} from '../api/client.js';

import KnowledgeTreeDialog from '../components/dialog/component/KnowledgeTreeDialog.jsx';

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter("file");
    if (!file) {
        alert("未指定文件.");
        return;
    }
    window._project = buildProjectNameFromFile(file);

    let toolbarApi: any = null;

    const cardTable = new ScoreCardTable({
        container: document.getElementById("tableContainer")!,
        headers: []
    });

    function doSave(newVersion: boolean) {
        try {
            let content = cardTable.toXml();
            content = encodeURIComponent(content);
            const url = "/common/saveFile";
            if (newVersion) {
                saveNewVersion(url, { file, content }).then(function () {
                    window.bootbox.alert("保存成功", function () {
                        toolbarApi.clearDirty();
                    });
                }).catch(function () {});
            } else {
                save(url, {content, file, newVersion: String(newVersion)} as unknown as Record<string, string>).then(function () {
                    window.bootbox.alert("保存成功", function () {
                        toolbarApi.clearDirty();
                    });
                });
            }
        } catch (error: any) {
            window.bootbox.alert(error.message || error);
        }
    }

    const decodedFile = decodeURIComponent(file);

    createRoot(document.getElementById("toolbarContainer")!).render(
        <EditorToolbar
            onSave={doSave}
            onReady={(api: any) => { toolbarApi = api; }}
            extraButtons={[
                <button key="addAttr" type="button" className="btn btn-default"
                        onClick={() => cardTable.addAttributeRow()}>
                    <i className="glyphicon glyphicon-plus"/> 添加属性行
                </button>,
                <button key="addCustomCol" type="button" className="btn btn-default"
                        onClick={() => cardTable.addCustomCol()}>
                    <i className="glyphicon glyphicon-plus-sign"/> 添加自定义列
                </button>,
                <button key="test" type="button" className="btn btn-success"
                        onClick={() => event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile, type: 'scorecardLib'})}>
                    <i className="glyphicon glyphicon-flash"/> 快速测试
                </button>
            ]}
        />
    );

    createRoot(document.getElementById("dialogContainer")!).render(
        <div>
            <KnowledgeTreeDialog/>
            <ConfigLibraryDialog/>
            <QuickTestDialog/>
        </div>,
    );

    loadEditorData(file).then(function (card: any) {
        cardTable.init(card);
        toolbarApi.clearDirty();
    }).catch(function (response: any) {
        if (response && response.status === 401) {
            window.bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function (text: string) {
                window.bootbox.alert("<span style='color: red'>加载数据失败,服务端错误：" + text + "</span>");
            });
        } else {
            window.bootbox.alert("<span style='color: red'>加载数据失败,服务端出错</span>");
        }
    });
});
