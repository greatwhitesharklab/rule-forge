/**
 * 应用内编辑器标签(EditorTabs)的编辑器类型映射:树节点 / 文件路径 → editorType。
 *
 * <p>本模块只做纯逻辑映射,不 import 任何编辑器组件(组件注册表在
 * {@link ./editorRegistry.tsx}),文件树等轻量调用方可安全引用而不拖入编辑器 bundle。
 *
 * <p>类型映射复用(并修正一处顺序)原 treeDataUtils.handleFileOpen 的分支判定:
 * type 直配 > 文件扩展名;细分 v1 双后缀先于 `.json` 通配。
 */

/** 应用内标签支持的编辑器类型(与 editorRegistry 的注册表 key 一一对应)。 */
export type EditorType =
    | 'resource' | 'client' | 'permission'
    | 'drl' | 'dmn' | 'pmml'
    | 'v1flow' | 'v1library' | 'v1ruleset' | 'v1decisiontable' | 'v1scorecard';

/** 无 file 时的固定标签名(权限配置是全局单例,file 无关)。 */
const EDITOR_FIXED_LABELS: Partial<Record<EditorType, string>> = {
    permission: '权限配置',
};

const EDITOR_TYPES: ReadonlySet<string> = new Set<EditorType>([
    'resource', 'client', 'permission', 'drl', 'dmn', 'pmml',
    'v1flow', 'v1library', 'v1ruleset', 'v1decisiontable', 'v1scorecard',
]);

/** 字符串是否合法 editorType。 */
export function isEditorType(type: string | undefined | null): type is EditorType {
    return !!type && EDITOR_TYPES.has(type);
}

/**
 * 标签显示名:file 末段(历史版本 ':版本号' 后缀原样带上);无 file 用固定名。
 */
export function editorTabLabel(editorType: EditorType, file?: string): string {
    if (file) {
        const seg = file.split('/').pop();
        if (seg) return seg;
    }
    return EDITOR_FIXED_LABELS[editorType] || editorType;
}

/**
 * 树节点 → editorType。type 直配 > 文件扩展名;细分 v1 双后缀(.v1lib.json 等)先于
 * v1flow 的 `.json` 通配判定 —— 修正原 handleFileOpen 把 .v1lib.json 等误开成 v1flow 的顺序问题
 * (原实现 v1flow 分支含 `.json` 兼容后缀且排在最前)。
 *
 * 老 4 库 .vl/.cl/.pl/.al.xml 不在此列 —— 走只读源码查看,由调用方先判。
 *
 * @returns 命中的 editorType;未匹配(如老 .rs.xml 等无编辑器类型)返回 null
 */
export function treeNodeToEditorType(data: TreeNodeData, treeType?: string): EditorType | null {
    const fullPath = typeof data.fullPath === 'string' ? data.fullPath : '';
    if (data.type === 'drl' || fullPath.endsWith('.drl')) return 'drl';
    if (data.type === 'v1library' || fullPath.endsWith('.v1lib.json')) return 'v1library';
    if (data.type === 'v1ruleset' || fullPath.endsWith('.v1rs.json')) return 'v1ruleset';
    if (data.type === 'v1decisiontable' || fullPath.endsWith('.v1dt.json')) return 'v1decisiontable';
    if (data.type === 'v1scorecard' || fullPath.endsWith('.v1sc.json')) return 'v1scorecard';
    // V1 决策流:.v1flow.json 统一后缀;.json 兼容旧文件(放在细分 v1 后缀之后,只兜真正的旧文件)
    if (data.type === 'v1flow' || fullPath.endsWith('.v1flow.json') || fullPath.endsWith('.json')) return 'v1flow';
    if (data.type === 'dmn' || fullPath.endsWith('.dmn')) return 'dmn';
    if (data.type === 'pmml' || fullPath.endsWith('.pmml')) return 'pmml';
    // 公共资源树(treeType==='public')统一走 resource 编辑器
    if (treeType === 'public') return 'resource';
    return null;
}

/**
 * 文件路径 → editorType(ReferenceDialog 等只有路径没有树节点 type 的场景,按扩展名判定)。
 * 细分双后缀(.v1lib.json 等)先于 .json 通配判定;未命中返回 null。
 */
export function filePathToEditorType(fullPath: string): EditorType | null {
    if (!fullPath) return null;
    const lower = fullPath.toLowerCase();
    if (lower.endsWith('.v1flow.json')) return 'v1flow';
    if (lower.endsWith('.v1lib.json')) return 'v1library';
    if (lower.endsWith('.v1rs.json')) return 'v1ruleset';
    if (lower.endsWith('.v1dt.json')) return 'v1decisiontable';
    if (lower.endsWith('.v1sc.json')) return 'v1scorecard';
    if (lower.endsWith('.json')) return 'v1flow';
    if (lower.endsWith('.drl')) return 'drl';
    if (lower.endsWith('.dmn')) return 'dmn';
    if (lower.endsWith('.pmml')) return 'pmml';
    return null;
}
