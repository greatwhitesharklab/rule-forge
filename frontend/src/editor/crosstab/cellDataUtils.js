/**
 * cellDataUtils - Utility functions for copying and pasting cell data.
 *
 * These functions communicate with the server to serialize/deserialize
 * cell data (conditions and values) for clipboard operations.
 *
 * Extracted from the crosstab webpack bundle (module 16).
 */

/**
 * Copy cell data to the server-side clipboard.
 *
 * @param {string} type - "condition" or "value"
 * @param {string} xml - The XML representation of the cell data
 */
export function copyCellData(type, xml) {
    fetch(window._server + '/common/parseCellData', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({type: type, xml: xml}).toString()
    });
}

/**
 * Paste cell data from the server-side clipboard.
 *
 * @param {string} type - "condition" or "value"
 * @param {Function} callback - Called with the parsed data on success
 */
export function pasteCellData(type, callback) {
    fetch(window._server + '/common/loadCellData', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({type: type}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        if (data) {
            callback(data);
        } else {
            window.bootbox.alert('当前没有数据可供粘贴！');
        }
    }).catch(function (error) {
        if (error) {
            window.bootbox.alert('粘贴失败: ' + (error.message || error));
        }
    });
}
