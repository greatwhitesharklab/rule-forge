import type {ReactNode} from 'react';
import {ResourceEditorView} from '@/resource/EditorRoute';
import {ClientEditor} from '@/client/EditorRoute';
import {PermissionEditor} from '@/permission/EditorRoute';
import {DrlEditorView} from '@/editor/drleditor/EditorRoute';
import {DmnEditor} from '@/editor/dmn/EditorRoute';
import {PmmlEditor} from '@/editor/pmml/EditorRoute';
import {V1FlowDesigner} from '@/v1-flow/EditorRoute';
import {V1LibraryEditor} from '@/v1-flow/LibraryEditorRoute';
import {V1RuleSetEditor} from '@/v1-flow/RuleSetRoute';
import {V1DecisionTableEditor} from '@/v1-flow/DecisionTableRoute';
import {V1ScoreCardEditor} from '@/v1-flow/ScoreCardRoute';
import type {EditorType} from './editorTypeMap';

/**
 * editorType → 编辑器组件注册表(EditorTabs 宿主按此渲染标签内容)。
 *
 * <p>11 个编辑器均已组件化(命名导出、吃 props、不依赖路由),这里只做装配:
 * file 原样透传(历史版本 ':版本号' 后缀也原样,与路由壳行为一致)。
 */

interface EditorDef {
    /** 渲染编辑器本体;file 对 permission 这类全局单例无意义 */
    render: (file?: string) => ReactNode;
}

export const EDITOR_REGISTRY: Record<EditorType, EditorDef> = {
    resource: {render: (file) => <ResourceEditorView file={file || ''}/>},
    // client 编辑器吃 project 名:file 槽位直接透传项目名(与路由壳 file→project 回退一致)
    client: {render: (file) => <ClientEditor project={file || ''}/>},
    // 权限配置是全局单例(file 无关)
    permission: {render: () => <PermissionEditor/>},
    drl: {render: (file) => <DrlEditorView file={file || ''}/>},
    dmn: {render: (file) => <DmnEditor file={file || ''}/>},
    pmml: {render: (file) => <PmmlEditor file={file || ''}/>},
    v1flow: {render: (file) => <V1FlowDesigner file={file || ''}/>},
    v1library: {render: (file) => <V1LibraryEditor file={file}/>},
    v1ruleset: {render: (file) => <V1RuleSetEditor file={file}/>},
    v1decisiontable: {render: (file) => <V1DecisionTableEditor file={file}/>},
    v1scorecard: {render: (file) => <V1ScoreCardEditor file={file}/>},
};
