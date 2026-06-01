import { Component, createRef } from 'react';
import * as componentEvent from '../componentEvent.ts';

interface CellEditorProps {
    header: GridColumnHeader;
    onchange?: (value: string) => void;
    onblur?: (e: React.FocusEvent<HTMLElement>) => void;
    display?: string;
}

interface CellEditorState {
    display: string;
    rowData: Record<string, unknown> | null;
    value: string;
}

export interface GridColumnHeader {
    id: string;
    name: string;
    label: string;
    editable?: boolean;
    width?: string;
    editorType?: string;
    selectData?: string[];
    selectParam?: string;
    filterable?: boolean;
    hideFilterRow?: boolean;
    dateFormat?: string;
    formatter?: (data: unknown) => React.ReactNode;
}

export default class CellEditor extends Component<CellEditorProps, CellEditorState> {
    private inputRef = createRef<HTMLInputElement | HTMLSelectElement>();

    constructor(props: CellEditorProps) {
        super(props);
        this.state = { display: 'none', rowData: null, value: '' };
    }

    componentDidMount() {
        componentEvent.eventEmitter.on(componentEvent.SHOW_CELL_EDITOR, (data: { colId: string; rowData: Record<string, unknown> }) => {
            const header = this.props.header;
            if (data.colId !== header.id) {
                return;
            }
            const rowData = data.rowData;
            this.setState({ rowData, display: '', value: String(rowData[header.name] ?? '') });
        });
    }

    componentWillUnmount() {
        componentEvent.eventEmitter.removeAllListeners(componentEvent.SHOW_CELL_EDITOR);
    }

    blur() {
        const value = this.inputRef.current ? this.inputRef.current.value : '';
        const header = this.props.header;
        const rowData = this.state.rowData;
        if (rowData) {
            rowData[header.name] = value;
        }
        this.setState({ display: 'none' });
    }

    componentDidUpdate(prevProps: CellEditorProps, prevState: CellEditorState) {
        if (this.state.display === '' && prevState.display !== '' && this.inputRef.current) {
            this.inputRef.current.focus();
        }
    }

    render() {
        const styleObj: React.CSSProperties = { display: this.state.display, height: '31px', padding: '0px 5px' };
        const header = this.props.header;
        const { editorType, selectData, selectParam } = header;
        const currentValue = this.state.value || '';
        switch (editorType) {
            case "select": {
                let selectOptions: string[] = [];
                selectOptions = selectParam && this.state.rowData && (this.state.rowData as Record<string, unknown>)[selectParam]
                    ? (this.state.rowData as Record<string, string[]>)[selectParam]
                    : (selectData || []);
                return (<select ref={this.inputRef as React.RefObject<HTMLSelectElement>} style={styleObj} onBlur={this.blur.bind(this)}
                    className="form-control" defaultValue={currentValue}>
                    {selectOptions.map((option, index) => {
                        return (<option key={index}>{option}</option>);
                    })}
                </select>);
            }
            case "boolean":
                return (
                    <select ref={this.inputRef as React.RefObject<HTMLSelectElement>} onBlur={this.blur.bind(this)} className="form-control"
                        defaultValue={currentValue}>
                        <option value="true">true</option>
                        <option value="false">false</option>
                    </select>
                );
            case "date":
                return (<input ref={this.inputRef as React.RefObject<HTMLInputElement>} style={styleObj} onBlur={this.blur.bind(this)} type="date"
                    className="form-control" defaultValue={currentValue} />);
            case "number":
                return (<input ref={this.inputRef as React.RefObject<HTMLInputElement>} style={styleObj} onBlur={this.blur.bind(this)} type="number"
                    className="form-control" defaultValue={currentValue} />);
            default:
                return (<input ref={this.inputRef as React.RefObject<HTMLInputElement>} style={styleObj} onBlur={this.blur.bind(this)} type="text"
                    className="form-control" defaultValue={currentValue} />);
        }
    }
}
