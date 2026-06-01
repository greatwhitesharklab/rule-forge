import 'bootstrap/dist/css/bootstrap.min.css';
import '../../bootbox.js';
/**
 * Complex Scorecard Editor - Entry point.
 *
 * Replaces the original jQuery-based bootstrap from the webpack bundle.
 * Initializes the ComplexScoreCard, React EditorToolbar, save/load logic, and dialogs.
 */

import '../context.standalone.css';
import '../../css/iconfont.css';
import '../ruleforge/ruleset.css';
import '../../css/tailwind-base.css';
import '../uuid.js';
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

import React from 'react';
import {createRoot} from 'react-dom/client';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import EditorToolbar from '../../components/editor-toolbar/EditorToolbar.jsx';

import ComplexScoreCard from './ComplexScoreCard';
import {getParameter, buildProjectNameFromFile} from '../../Utils.js';

let scoreCardInstance: ComplexScoreCard | null = null;

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    if (!file) {
        document.getElementById('container')!.innerHTML = '<h2 style="color:red">请先指定一个复杂评分卡文件!</h2>';
        return;
    }
    window._project = buildProjectNameFromFile(file);

    // Initialize the complex scorecard
    scoreCardInstance = new ComplexScoreCard(document.getElementById('container')!);

    // Render EditorToolbar into toolbarContainer
    createRoot(document.getElementById('toolbarContainer')!).render(
        <EditorToolbar
            onSave={(isNewVersion: boolean) => {
                if (scoreCardInstance) {
                    scoreCardInstance.save(isNewVersion);
                }
            }}
            extraButtons={[
                <button key="addCriteria" type="button" className="btn btn-default"
                        onClick={() => {
                            if (scoreCardInstance) scoreCardInstance.addCriteriaRow();
                        }}>
                    <i className="glyphicon glyphicon-plus" style={{fontSize: '16px'}}/> 添加条件行
                </button>,
                <button key="deleteCriteria" type="button" className="btn btn-default"
                        onClick={() => {
                            if (scoreCardInstance) scoreCardInstance.deleteCriteriaRow();
                        }}>
                    <i className="glyphicon glyphicon-minus" style={{fontSize: '16px'}}/> 删除条件行
                </button>
            ]}
        />
    );

    // Render dialogs
    createRoot(document.getElementById('dialogContainer')!).render(
        <div>
            <KnowledgeTreeDialog/>
            <ConfigLibraryDialog/>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
        </div>
    );
});
