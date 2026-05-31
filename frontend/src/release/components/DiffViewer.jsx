import {Component} from 'react';
import {Diff2Html} from 'diff2html';
import 'diff2html/bundles/css/diff2html.min.css';

export default class DiffViewer extends Component {
    render() {
        const {patch, filePath} = this.props;
        if (!patch) {
            return <div style={{padding: 20, color: '#999', textAlign: 'center'}}>无差异</div>;
        }

        const html = Diff2Html.getPrettyHtml(patch, {
            inputFormat: 'diff',
            outputFormat: 'side-by-side',
            drawFileList: false,
            matching: 'lines',
            synchronisedScroll: true
        });

        return (
            <div>
                {filePath && <div style={{padding: '5px 10px', background: '#f5f5f5', borderBottom: '1px solid #e0e0e0', fontSize: 13, fontWeight: 500}}>{filePath}</div>}
                <div dangerouslySetInnerHTML={{__html: html}}/>
            </div>
        );
    }
}
