import '../../bootbox.js';
import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../context.standalone.css';
import './ruleset.css';
import '../../css/iconfont.css';
import '../Math.uuid.js';
import '../common/contextMenu.js';
import '../common/URule.js';
import '../common/Context.js';
import '../../Remark.js';
import './RuleFactory.js';
import './RuleProperty.js';
import '../common/ComparisonOperator.js';
import '../common/ComplexArithmetic.js';
import '../common/VariableValue.js';
import '../common/ResourceListDialog.js';
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
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import EditorToolbar from '../../components/editor-toolbar/EditorToolbar.jsx';
import ReferenceDialog from '../../reference/ReferenceDialog.jsx';
import * as refEvent from '../../reference/event.js';
import * as componentEvent from '../../components/componentEvent.js';
import {createRoot} from 'react-dom/client';
import {getParameter, ajaxSave, saveNewVersion, buildProjectNameFromFile, loadEditorData, handleResponseError} from "../../Utils.js";

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    if (!file || file.length < 1) {
        bootbox.alert("当前编辑器未指定具体规则文件！");
        return;
    }

    window._project = buildProjectNameFromFile(file);
    window.refEvent = refEvent;

    const factory = new RuleFactory(document.getElementById('container'));
    factory.setFile(file);

    let toolbarApi = null;

    function handleSave(isNewVersion) {
        var xml;
        try {
            xml = factory.toXml();
        } catch (error) {
            MsgBox.alert(error);
            return;
        }
        xml = encodeURIComponent(xml);
        var postData = {content: xml, file: file, newVersion: isNewVersion};
        var url = window._server + '/common/saveFile';
        if (isNewVersion) {
            saveNewVersion(url, postData, function () {
                toolbarApi.clearDirty();
                bootbox.alert('保存成功!');
            });
        } else {
            ajaxSave(url, postData, function () {
                toolbarApi.clearDirty();
                bootbox.alert('保存成功!');
            });
        }
    }

    function addRule() {
        var ruleKey = prompt("规则编号", "");
        if (ruleKey == null || ruleKey === '') {
            var rule = factory.addRule();
            rule.initTopJoin();
        } else {
            var projectName = file.split('/')[1];
            fetch(window._server + '/common/findRuleByKey', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: new URLSearchParams({ruleKey: ruleKey, projectName: projectName}).toString()
            }).then(function(response) {
                if (!response.ok) throw response;
                return response.json();
            }).then(function (res) {
                if (res != null && res.length > 0) {
                    factory.addRule(res[0]);
                } else {
                    var rule = factory.addRule();
                    rule.initTopJoin();
                }
            }).catch(function() {});
        }
    }

    var decodedFile = decodeURIComponent(file);

    createRoot(document.getElementById('toolbarContainer')).render(
        <EditorToolbar
            onSave={handleSave}
            onReady={(api) => { toolbarApi = api; }}
            extraButtons={[
                <button key="addRule" type="button" className="btn btn-default btn-sm"
                        onClick={addRule}>
                    <i className="glyphicon glyphicon-plus-sign"/> 添加规则
                </button>,
                <button key="addLoopRule" type="button" className="btn btn-default btn-sm"
                        onClick={() => { var rule = factory.addLoopRule(); rule.initTopJoin(); }}>
                    <i className="glyphicon glyphicon-plus"/> 添加循环规则
                </button>,
                <button key="test" type="button" className="btn btn-success"
                        onClick={() => componentEvent.eventEmitter.emit(componentEvent.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile, type: 'ruleLib'})}>
                    <i className="glyphicon glyphicon-flash"/> 快速测试
                </button>,
                <button key="reference" type="button" className="btn btn-info"
                        onClick={() => {
                            var title = '规则集"' + decodedFile + '"';
                            if (window.refEvent) {
                                window.refEvent.eventEmitter.emit(window.refEvent.OPEN_REFERENCE_DIALOG, {path: decodedFile}, title);
                            }
                        }}>
                    <i className="rf rf-link"/> 查看引用
                </button>
            ]}
        />
    );

    createRoot(document.getElementById('dialogContainer')).render(
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
    loadEditorData(file).then(function (editorData) {
        factory.loadData(editorData);
        toolbarApi.clearDirty();
    }).catch(function (response) {
        handleResponseError(response, '加载文件失败，服务端错误：');
    });
});
