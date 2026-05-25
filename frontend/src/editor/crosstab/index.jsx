import '../../bootbox.js';

import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
import './crosstab.css';
import '../ruleforge/ruleset.css';
import '../Math.uuid.js';
import '../../Remark.js';
import '../common/contextMenu.js';
import '../common/URule.js';
import '../common/Context.js';
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
import '../ruleforge/RuleProperty.js';
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
import '../decisiontable/Join.js';
import '../decisiontable/Condition.js';
import '../decisiontable/CellCondition.js';

import CrossTable from './CrossTable.js';
import ExcelImportDialog from './ExcelImportDialog.js';
import {getParameter, ajaxSave, buildProjectNameFromFile, loadEditorData} from '../../Utils.js';
import {createRoot} from 'react-dom/client';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import EditorToolbar from '../../components/editor-toolbar/EditorToolbar.jsx';

document.addEventListener('DOMContentLoaded', function () {
    const crossTable = new CrossTable({
        container: document.getElementById('container')
    });

    const file = getParameter('file');
    if (!file) {
        document.getElementById('container').innerHTML = '<h2 style="color:red">请先指定一个交叉决策表文件!</h2>';
        return;
    }

    window._project = buildProjectNameFromFile(file);

    let toolbarApi = null;

    function saveFile(isNewVersion) {
        let xml = null;
        try {
            xml = crossTable.toXml();
        } catch (e) {
            window.bootbox.alert(e.message || e);
            return;
        }
        if (!xml) return;

        xml = encodeURIComponent(xml);
        const saveUrl = window._server + '/common/saveFile';

        if (isNewVersion) {
            bootbox.prompt('请输入新版本描述.', function (comment) {
                if (comment) {
                    ajaxSave(saveUrl, {
                        content: xml,
                        file: file,
                        newVersion: isNewVersion,
                        versionComment: comment
                    }, function () {
                        toolbarApi.clearDirty();
                    });
                }
            });
        } else {
            ajaxSave(saveUrl, {
                content: xml,
                file: file,
                newVersion: isNewVersion
            }, function () {
                toolbarApi.clearDirty();
            });
        }
    }

    createRoot(document.getElementById('toolbarContainer')).render(
        <EditorToolbar
            onSave={saveFile}
            onReady={(api) => { toolbarApi = api; }}
            extraButtons={[
                <button key="excel" type="button" className="btn btn-default" style={{height: '36px'}}
                        onClick={() => new ExcelImportDialog().show()}>
                    <i className="glyphicon glyphicon-share-alt" style={{fontSize: '16px'}}></i> 导入Excel
                </button>
            ]}
        />
    );

    createRoot(document.getElementById('dialogContainer')).render(
        <div>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
            <ConfigLibraryDialog/>
        </div>
    );

    // Load the crosstab data from server
    const doImport = getParameter('doImport');
    var extraParams = null;
    if (doImport && doImport.length > 1) {
        extraParams = {doImport: 'true'};
    }

    loadEditorData(file, extraParams).then(function (data) {
        data.cellsMap = function (tableData) {
            const map = new Map();
            const cells = tableData.cells;
            for (const cell of cells) {
                const key = cell.row + ',' + cell.col;
                map.set(key, cell);
            }
            return map;
        }(data);

        crossTable.init(data);
        toolbarApi.clearDirty();
    }).catch(function (error) {
        document.body.innerHTML = '';
        if (error && error.status === 401) {
            window.bootbox.alert('权限不足，不能进行此操作.');
        } else if (error && error.text) {
            error.text().then(function(text) {
                try {
                    const result = JSON.parse(text);
                    window.bootbox.alert("<span style='color: red'>服务端错误：" + result.errorMsg + '</span>');
                } catch (e) {
                    window.bootbox.alert("<span style='color: red'>服务端错误：" + text + '</span>');
                }
            });
        } else {
            window.bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
});
