import type {GridOptions} from 'ag-grid-community';

/** ag-grid 中文文案(只配常见几项,其余保留默认英文)。
 *  决策表编辑器 / 节点属性抽屉共用,避免两处各写一份。 */
export const AG_GRID_LOCALE_ZH: NonNullable<GridOptions['localeText']> = {
    noRowsToShow: '暂无数据',
    loadingOoo: '加载中…',
    page: '页',
    more: '更多',
    to: '至',
    of: '共',
};
