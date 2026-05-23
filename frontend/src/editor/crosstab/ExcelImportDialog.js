/**
 * ExcelImportDialog - Dialog for importing Excel files into the crosstab editor.
 *
 * Provides a file upload form that posts to the server's import endpoint.
 * Uses an iframe for the upload response to avoid page navigation.
 *
 * Extracted from the crosstab webpack bundle (module 456).
 */

import {getParameter} from '../../Utils.js';

export default class ExcelImportDialog {
    /**
     * @param {Function} [callback] - Callback after successful import
     */
    constructor(callback) {
        this.callback = callback;
        this.dialog = $(
            '<div class="modal fade" role="dialog" aria-hidden="true" style="z-index: 10000">' +
            '  <div class="modal-dialog">' +
            '    <div class="modal-content">' +
            '      <div class="modal-header">' +
            '        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>' +
            '        <h4 class="modal-title">导入Excel</h4>' +
            '      </div>' +
            '      <div class="modal-body"></div>' +
            '      <div class="modal-footer"></div>' +
            '    </div>' +
            '  </div>' +
            '</div>'
        );

        const body = this.dialog.find('.modal-body');
        const footer = this.dialog.find('.modal-footer');
        this.initBody(body, footer, callback);
    }

    /**
     * Build the dialog body content: tutorial link, file upload form, and hidden iframe.
     *
     * @param {jQuery} body - The modal body element
     * @param {jQuery} footer - The modal footer element
     * @param {Function} callback - Callback after successful import
     */
    initBody(body, footer, callback) {
        const tutorialLink = $('<div><a href="http://wiki.bsdn.org/pages/viewpage.action?pageId=76450722" target="_blank">导入Excel教程</a></div>');
        body.append(tutorialLink);

        const uploadUrl = window._server + '/crosstabeditor/importExcel?project=' + encodeURI(window._project);
        const form = $('<form enctype="multipart/form-data" style="height: 70px;" method="post" target="frame_for_import" action="' + uploadUrl + '"></form>');
        body.append(form);

        const fileGroup = $('<div class="form-group"><label>请选择要导入的Excel文件：</label></div>');
        form.append(fileGroup);

        const fileInput = $('<input type="file" name="excel_file" style="display: inline-block">');
        fileGroup.append(fileInput);

        const submitBtn = $('<input type="submit" value="上传" class="btn btn-default" style="float: right">');
        form.append(submitBtn);

        // Hidden iframe to receive the upload response
        const iframe = $('<iframe name="frame_for_import" style="width: 0;height: 0;border: 0px"></iframe>');
        body.append(iframe);

        iframe.load(function () {
            const responseText = $(this).contents().find('body').text();
            if (responseText && responseText.length >= 5) {
                const result = JSON.parse(responseText);
                if (result.fail) {
                    bootbox.alert('Excel导入失败：<span style="color: #d30e00;">' + result.msg + '</span>');
                } else {
                    const file = getParameter('file');
                    const reloadUrl = window._server + '/crosstabeditor?file=' + file + '&doImport=true';
                    window.open(reloadUrl, '_self');
                }
            }
        });
    }

    /**
     * Show the dialog.
     */
    show() {
        this.dialog.modal('show');
    }
}
