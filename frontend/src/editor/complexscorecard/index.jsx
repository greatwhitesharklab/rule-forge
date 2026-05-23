/**
 * Complex Scorecard Editor - Entry point.
 *
 * Replaces the original jQuery-based bootstrap from the webpack bundle.
 * Initializes the ComplexScoreCard, toolbar, save/load logic, and dialogs.
 */

import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
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
import '../common/jquery.utils.js';

import React from 'react';
import {createRoot} from 'react-dom/client';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';

import ComplexScoreCard from './ComplexScoreCard.js';
import {getParameter, buildProjectNameFromFile} from '../../Utils.js';

window._setDirty = function () {
    if (window._dirty) return;
    window._dirty = true;
    $('#saveButton').html("<i class='rf rf-save'></i> *保存");
    $('#saveButton').prop('disabled', false);
};

window.cancelDirty = function () {
    if (!window._dirty) return;
    window._dirty = false;
    $('#saveButton').html("<i class='rf rf-save'></i> 保存");
    $('#saveButton').prop('disabled', true);
};

$(document).ready(function () {
    const file = getParameter('file');
    if (!file) {
        $('#container').html('<h2 style="color:red">请先指定一个复杂评分卡文件!</h2>');
        return;
    }
    window._project = buildProjectNameFromFile(file);

    // Render KnowledgeTreeDialog
    createRoot(document.getElementById('dialogContainer')).render(
        <KnowledgeTreeDialog/>
    );

    // Initialize the complex scorecard
    new ComplexScoreCard($('#container'));
});
