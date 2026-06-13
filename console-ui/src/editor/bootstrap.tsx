/**
 * Unified editor bootstrap — reads ?type= from URL and dynamically imports
 * the corresponding editor entry point.
 */
import './onDomReady'; // patch DOMContentLoaded for dynamic imports

const type = new URLSearchParams(window.location.search).get('type') || '';

const editors: Record<string, () => Promise<unknown>> = {
    variable:              () => import('../variable/index.tsx'),
    constant:              () => import('../constant/index.tsx'),
    parameter:             () => import('../parameter/index.tsx'),
    action:                () => import('../action/index.tsx'),
    package:               () => import('../package/index.tsx'),
    client:                () => import('../client/index.tsx'),
    permission:            () => import('../permission/index.tsx'),
    resource:              () => import('../resource/index.tsx'),
    monitoring:            () => import('../monitoring/index.tsx'),
    analysis:              () => import('../analysis/index.tsx'),
    ruleset:               () => import('./ruleforge/index.jsx'),
    decisiontable:         () => import('./decisiontable/index.jsx'),
    scriptdecisiontable:   () => import('./scriptdecisiontable/index.jsx'),
    decisiontree:          () => import('./decisiontree/index.jsx'),
    crosstab:              () => import('./crosstab/index.jsx'),
    complexscorecard:      () => import('./complexscorecard/index.jsx'),
    drl:                   () => import('./drleditor/index.jsx'),
    flowbpmn:              () => import('../flow-bpmn/index.jsx'),
    scorecard:             () => import('../scorecard/index.jsx'),
    ul:                    () => import('./ul/index.jsx'),
};

const loader = editors[type];
if (loader) {
    loader().catch((e: unknown) => console.error('Failed to load editor:', type, e));
} else {
    document.title = 'Error';
    const c = document.getElementById('container');
    if (c) c.innerHTML = '<h2>未知编辑器类型: ' + type + '</h2>';
}
