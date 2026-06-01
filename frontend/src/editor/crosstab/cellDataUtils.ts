/**
 * cellDataUtils - Utility functions for copying and pasting cell data.
 *
 * These functions communicate with the server to serialize/deserialize
 * cell data (conditions and values) for clipboard operations.
 *
 * Extracted from the crosstab webpack bundle (module 16).
 */

import { formPost } from '../../api/client.js';

/**
 * Copy cell data to the server-side clipboard.
 *
 * @param type - "condition" or "value"
 * @param xml - The XML representation of the cell data
 */
export function copyCellData(type: string, xml: string): void {
    formPost('/common/parseCellData', { type, xml });
}

/**
 * Paste cell data from the server-side clipboard.
 *
 * @param type - "condition" or "value"
 * @param callback - Called with the parsed data on success
 */
export function pasteCellData(type: string, callback: (data: any) => void): void {
    formPost<any>('/common/loadCellData', { type })
        .then(function (data: any) {
            if (data) {
                callback(data);
            } else {
                window.bootbox.alert('当前没有数据可供粘贴！');
            }
        })
        .catch(function () {
            window.bootbox.alert('粘贴失败');
        });
}
