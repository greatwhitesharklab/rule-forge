import {html as diff2htmlRender, Diff2HtmlConfig} from 'diff2html';
import {formPost} from '../../api/client.js';

const DiffDialog = {
    show(projectName: string, fromVersion: string, toVersion: string, filePath?: string) {
        const params: Record<string, string> = {fromVersion, toVersion};
        const endpoint = filePath ? 'getFileDiffStructured' : 'getPackageDiffStructured';
        if (filePath) {
            params.filePath = filePath;
        } else {
            params.project = projectName;
        }

        formPost('/packageeditor/' + endpoint, params).then(data => {
            const diffs = Array.isArray(data) ? data : data ? [data] : [];
            const content = diffs.filter(Boolean).map((d: any) => d.patch || '').join('\n');

            if (content) {
                try {
                    const renderedHtml = diff2htmlRender(content, {
                        outputFormat: 'side-by-side' as const,
                        drawFileList: false,
                        matching: 'lines' as const
                    } as Diff2HtmlConfig);
                    window.bootbox.dialog({
                        title: '版本差异: ' + fromVersion + ' → ' + toVersion,
                        message: '<div style="max-height:70vh;overflow:auto;">' + renderedHtml + '</div>',
                        size: 'large',
                        buttons: {close: {label: '关闭', className: 'btn-default'}}
                    });
                } catch (_e) {
                    DiffDialog._showPlainText(content, fromVersion, toVersion);
                }
            } else {
                window.bootbox.alert('无差异内容');
            }
        })
        .catch(err => {
            console.error('加载差异失败', err);
            window.bootbox.alert('加载差异失败');
        });
    },

    _showPlainText(content: string, fromVersion: string, toVersion: string) {
        const escaped = content.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        window.bootbox.dialog({
            title: '版本差异: ' + fromVersion + ' → ' + toVersion,
            message: '<pre style="max-height:70vh;overflow:auto;font-size:12px;">' + escaped + '</pre>',
            size: 'large',
            buttons: {close: {label: '关闭', className: 'btn-default'}}
        });
    }
};

export default DiffDialog;
