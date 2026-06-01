import '../../bootbox.js';
import 'handsontable/styles/handsontable.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
import '../ruleforge/ruleset.css';
import '../../css/tailwind-base.css';
import '../uuid.js';
import '../../Remark.ts';
import '../common/URule.js';
import '../common/contextMenu.js';
import '../common/Context.js';
import '../ruleforge/RuleFactory.js';
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
import './Join.js';
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
import './Condition.js';
import './CellCondition.js';
import './CellContent.js';
import './CellExecuteMethod.js';
import './renderers.js';
import '../ruleforge/Rule.js';
import './DecisionTable.js';
import React from 'react';
import QuickTestDialog from '../../components/dialog/component/QuickTestDialog.jsx';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import { createRoot } from 'react-dom/client';
import { buildProjectNameFromFile, getParameter } from '../../Utils';

declare const RuleForge: {
    DecisionTable: new (id: string) => any;
};

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    createRoot(document.getElementById('dialogContainer')!).render(
        <div>
            <KnowledgeTreeDialog/>
            <ConfigLibraryDialog/>
            <QuickTestDialog/>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
        </div>,
    );
    new RuleForge.DecisionTable('container');
});
