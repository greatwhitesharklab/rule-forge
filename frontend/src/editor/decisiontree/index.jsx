import '../../bootbox.js';
import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
import './decision-tree.css';
import '../ruleforge/ruleset.css';
import '../Math.uuid.js';
import '../../Remark.js';
import '../common/contextMenu.js';
import '../common/URule.js';
import '../common/ComparisonOperator.js';
import '../common/ComplexArithmetic.js';
import '../common/VariableValue.js';
import '../common/ResourceListDialog.js';
import '../common/ResourceVersionDialog.js';
import '../common/ConstantValue.js';
import '../ruleforge/ConfigActionDialog.js';
import '../ruleforge/ConfigConstantDialog.js';
import '../ruleforge/ConfigParameterDialog.js';
import '../ruleforge/ConfigVariableDialog.js';
import '../ruleforge/ActionType.js';
import '../ruleforge/PrintAction.js';
import '../ruleforge/AssignmentAction.js';
import '../ruleforge/SimpleArithmetic.js';
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
import '../ruleforge/NamedReferenceValue.js';
import './ConditionLeft.js';
import '../ruleforge/RuleProperty.js';

import DecisionTree from './new/DecisionTree.js';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../../components/dialog/component/QuickTestDialog.jsx';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import EditorToolbar from '../../components/editor-toolbar/EditorToolbar.jsx';
import { createRoot } from 'react-dom/client';
import {getParameter, ajaxSave, saveNewVersion, buildProjectNameFromFile, loadEditorData, handleResponseError} from "../../Utils.js";
import * as event from '../../components/componentEvent.js';

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    if (!file || file.length < 1) {
        alert("未指定具体的决策树文件！");
        return;
    }

    window._project = buildProjectNameFromFile(file);

    const decisionTree = new DecisionTree(document.getElementById('container'));

    let toolbarApi = null;

    function saveFile(isNewVersion) {
        var xml;
        try {
            xml = decisionTree.toXml();
        } catch (error) {
            alert(error);
            return;
        }
        xml = encodeURIComponent(xml);
        var postData = {content: xml, file: file, newVersion: isNewVersion};
        var url = window._server + '/common/saveFile';
        if (isNewVersion) {
            saveNewVersion(url, postData, function () {
                toolbarApi.clearDirty();
                window.bootbox.alert('保存成功!');
            });
        } else {
            ajaxSave(url, postData, function () {
                toolbarApi.clearDirty();
                window.bootbox.alert('保存成功!');
            });
        }
    }

    function loadData() {
        loadEditorData(file).then(function (editorData) {
            decisionTree.loadData(editorData);
            toolbarApi.clearDirty();
        }).catch(function (response) {
            handleResponseError(response, '加载文件失败：');
        });
    }

    createRoot(document.getElementById('toolbarContainer')).render(
        <EditorToolbar
            onSave={saveFile}
            onReady={(api) => { toolbarApi = api; }}
            extraButtons={[
                <button key="quicktest" type="button" className="btn btn-success"
                        onClick={function() {
                            var decodedFile = decodeURIComponent(file);
                            event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile});
                        }}>
                    <i className="glyphicon glyphicon-flash"></i> 快速测试
                </button>
            ]}
        />
    );

    createRoot(document.getElementById('dialogContainer')).render(
        <div>
            <KnowledgeTreeDialog/>
            <ConfigLibraryDialog/>
            <QuickTestDialog/>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
        </div>
    );

    loadData();
});
