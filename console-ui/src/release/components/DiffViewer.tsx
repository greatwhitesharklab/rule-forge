import {Component, ReactNode} from 'react';
import {html as diff2htmlRender, Diff2HtmlConfig} from 'diff2html';
import 'diff2html/bundles/css/diff2html.min.css';

interface DiffViewerProps {
    patch: string;
    filePath?: string;
}

export default class DiffViewer extends Component<DiffViewerProps> {
    render(): ReactNode {
        const {patch, filePath} = this.props;
        if (!patch) {
            return <div style={{padding: 20, color: '#999', textAlign: 'center'}}>无差异</div>;
        }

        const renderedHtml = diff2htmlRender(patch, {
            outputFormat: 'side-by-side' as const,
            drawFileList: false,
            matching: 'lines' as const,
            synchronisedScroll: true
        } as Diff2HtmlConfig);

        return (
            <div>
                {filePath && <div style={{padding: '5px 10px', background: '#f5f5f5', borderBottom: '1px solid #e0e0e0', fontSize: 13, fontWeight: 500}}>{filePath}</div>}
                <div dangerouslySetInnerHTML={{__html: renderedHtml}}/>
            </div>
        );
    }
}
