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
    callback?: () => void;
    dialog: HTMLDivElement;

    /**
     * @param callback - Callback after successful import
     */
    constructor(callback?: () => void) {
        this.callback = callback;
        const dialog = document.createElement('div');
        dialog.className = 'modal fade';
        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-hidden', 'true');
        dialog.style.zIndex = '10000';
        dialog.innerHTML =
            '  <div class="modal-dialog">' +
            '    <div class="modal-content">' +
            '      <div class="modal-header">' +
            '        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>' +
            '        <h4 class="modal-title">导入Excel</h4>' +
            '      </div>' +
            '      <div class="modal-body"></div>' +
            '      <div class="modal-footer"></div>' +
            '    </div>' +
            '  </div>';
        this.dialog = dialog;

        const body = dialog.querySelector('.modal-body') as HTMLElement;
        const footer = dialog.querySelector('.modal-footer') as HTMLElement;
        this.initBody(body, footer, callback);
    }

    /**
     * Build the dialog body content: tutorial link, file upload form, and hidden iframe.
     *
     * @param body - The modal body element
     * @param footer - The modal footer element
     * @param callback - Callback after successful import
     */
    initBody(body: HTMLElement, footer: HTMLElement, callback?: () => void): void {
        const tutorialLink = document.createElement('div');
        tutorialLink.innerHTML = '<a href="http://wiki.bsdn.org/pages/viewpage.action?pageId=76450722" target="_blank">导入Excel教程</a>';
        body.appendChild(tutorialLink);

        const uploadUrl = window._server + '/crosstabeditor/importExcel?project=' + encodeURI(window._project || '');
        const form = document.createElement('form');
        form.setAttribute('enctype', 'multipart/form-data');
        form.style.height = '70px';
        form.method = 'post';
        form.target = 'frame_for_import';
        form.action = uploadUrl;
        body.appendChild(form);

        const fileGroup = document.createElement('div');
        fileGroup.className = 'form-group';
        fileGroup.innerHTML = '<label>请选择要导入的Excel文件：</label>';
        form.appendChild(fileGroup);

        const fileInput = document.createElement('input') as HTMLInputElement;
        fileInput.type = 'file';
        fileInput.name = 'excel_file';
        fileInput.style.display = 'inline-block';
        fileGroup.appendChild(fileInput);

        const submitBtn = document.createElement('input') as HTMLInputElement;
        submitBtn.type = 'submit';
        submitBtn.value = '上传';
        submitBtn.className = 'btn btn-default';
        submitBtn.style.float = 'right';
        form.appendChild(submitBtn);

        // Hidden iframe to receive the upload response
        const iframe = document.createElement('iframe') as HTMLIFrameElement;
        iframe.name = 'frame_for_import';
        iframe.style.cssText = 'width: 0;height: 0;border: 0px';
        body.appendChild(iframe);

        iframe.addEventListener('load', function () {
            const responseText = iframe.contentDocument!.body.textContent;
            if (responseText && responseText.length >= 5) {
                const result = JSON.parse(responseText);
                if (result.fail) {
                    window.bootbox.alert('Excel导入失败：<span style="color: #d30e00;">' + result.msg + '</span>');
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
    show(): void {
        document.body.appendChild(this.dialog);
        this.dialog.classList.add('in');
        this.dialog.style.display = 'block';
        this.dialog.setAttribute('aria-hidden', 'false');
        document.body.classList.add('modal-open');
        // Add backdrop
        const backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop fade in';
        backdrop.id = 'excel-import-backdrop';
        document.body.appendChild(backdrop);
        // Close handlers
        const self = this;
        const closeBtn = this.dialog.querySelector('.close') as HTMLElement | null;
        if (closeBtn) {
            closeBtn.onclick = function () { self.hide(); };
        }
        backdrop.addEventListener('click', function () { self.hide(); });
    }

    /**
     * Hide the dialog.
     */
    hide(): void {
        this.dialog.classList.remove('in');
        this.dialog.style.display = 'none';
        this.dialog.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('modal-open');
        const backdrop = document.getElementById('excel-import-backdrop');
        if (backdrop) backdrop.remove();
    }
}
