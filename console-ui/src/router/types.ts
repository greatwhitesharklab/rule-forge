/**
 * SPA 路由参数类型(spa-migration-plan.md 阶段 0)。
 *
 * 集中定义路由用到的参数类型,避免散落各处。editor 子路由化(阶段 3)时 EditorType
 * 对应 /app/editor/:type 的 :type 参数,与 editor.html?type=xxx 的 xxx 一一对应
 * (见 html/editor.html 的 titles/bodyStyles/dom switch-case)。
 */

/** 编辑器类型 — 对应原 editor.html?type=xxx 的 xxx 参数。 */
export type EditorType =
    | 'ruleset' | 'ul'
    | 'decisiontable' | 'scriptdecisiontable' | 'crosstab'
    | 'decisiontree'
    | 'scorecard' | 'complexscorecard'
    | 'flowbpmn'
    | 'variable' | 'constant' | 'parameter' | 'action'
    | 'package' | 'client' | 'permission' | 'resource'
    | 'monitoring' | 'analysis';
