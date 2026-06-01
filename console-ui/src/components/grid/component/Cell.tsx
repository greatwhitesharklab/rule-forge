import { Component, createRef } from 'react';
import * as componentEvent from '../componentEvent.ts';
import { formatDate } from '../../../Utils.js';
import type { GridColumnHeader } from './CellEditor.tsx';

interface CellProps {
    rowData: Record<string, unknown>;
    header: GridColumnHeader;
    onchange?: (newValue: string) => void;
}

interface CellState {
    editing: boolean;
    editorValue: string;
}

export default class Cell extends Component<CellProps, CellState> {
    private inputRef = createRef<HTMLInputElement | HTMLSelectElement>();

    constructor(props: CellProps) {
        super(props);
        this.state = { editing: false, editorValue: '' };
    }

    componentDidUpdate(prevProps: CellProps, prevState: CellState) {
        if (this.state.editing && !prevState.editing && this.inputRef.current) {
            this.inputRef.current.focus();
        }
    }

    _startEdit = () => {
        const { rowData, header } = this.props;
        if (!header.editable) return;

        this.setState({ editing: true, editorValue: String(rowData[header.name] || '') });
    };

    _handleBlur = (e: React.FocusEvent<HTMLInputElement | HTMLSelectElement>) => {
        const { rowData, header } = this.props;
        const value = e.target.value;
        rowData[header.name] = value;
        this.setState({ editing: false, editorValue: '' });
    };

    _handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        this.setState({ editorValue: e.target.value });
    };

    _handleDoubleClick = () => {
        const { rowData, header } = this.props;
        const colName = header.name;
        let editorValue = rowData[colName] as string;
        if (!editorValue || editorValue === '') {
            editorValue = '{}';
        }
        const cellJsonData = JSON.parse(editorValue) as { type?: string; rows?: Record<string, unknown>[] } || {};
        const rows: Record<string, unknown>[] = cellJsonData.rows || [];
        let targetType = cellJsonData.type;
        const callback = function (rows: Record<string, unknown>[]) {
            const jsonData = { type: targetType, rows };
            const data = JSON.stringify(jsonData);
            rowData[colName] = data;
        };
        if (!targetType) {
            const simulatorCategoryData = window.simulatorCategoryData || [];
            let optionsHtml = '';
            for (const category of simulatorCategoryData) {
                optionsHtml += `<option value="${category.clazz}">${category.name}</option>`;
            }
            optionsHtml += '<option value="" selected></option>';
            window.bootbox.dialog({
                title: '选择子对象类型',
                message: `<select class="form-control" id="bootbox-category-select">${optionsHtml}</select>`,
                callback: () => {
                    const selectEl = document.getElementById('bootbox-category-select') as HTMLSelectElement | null;
                    targetType = selectEl ? selectEl.value : '';
                    if (!targetType || targetType === '') {
                        window.bootbox.alert('请先选择子对象类型!');
                        return;
                    }
                    let categoryTarget: SimulatorCategoryItem | null = null;
                    for (const category of simulatorCategoryData) {
                        if (targetType === category.clazz) {
                            categoryTarget = category;
                            break;
                        }
                    }
                    const variables = categoryTarget?.variables || [];
                    componentEvent.eventEmitter.emit(componentEvent.SHOW_CHILD_LIST_DIALOG, {
                        variables,
                        rows,
                        callback
                    });
                }
            });
        } else {
            const simulatorCategoryData = window.simulatorCategoryData || [];
            let categoryTarget: SimulatorCategoryItem | null = null;
            for (const category of simulatorCategoryData) {
                if (targetType === category.clazz) {
                    categoryTarget = category;
                    break;
                }
            }
            const variables = categoryTarget?.variables || [];
            componentEvent.eventEmitter.emit(componentEvent.SHOW_CHILD_LIST_DIALOG, {
                variables,
                rows,
                callback
            });
        }
    };

    _renderEditor() {
        const { rowData, header } = this.props;
        const { editorValue } = this.state;
        const inputStyle: React.CSSProperties = { height: '31px' };
        const editorType = (rowData._editorType as string) || header.editorType;

        switch (editorType) {
            case 'select': {
                const selectData = header.selectData || [];
                return (
                    <select ref={this.inputRef as React.RefObject<HTMLSelectElement>} className="form-control" style={inputStyle}
                        value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}>
                        {selectData.map((option, index) => (
                            <option key={index}>{option}</option>
                        ))}
                    </select>
                );
            }
            case 'number':
                return (
                    <input ref={this.inputRef as React.RefObject<HTMLInputElement>} type="number" className="form-control" style={inputStyle}
                        value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur} />
                );
            case 'date':
                return (
                    <input ref={this.inputRef as React.RefObject<HTMLInputElement>} type="date" className="form-control" style={inputStyle}
                        value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur} />
                );
            case 'boolean':
                return (
                    <select ref={this.inputRef as React.RefObject<HTMLSelectElement>} className="form-control" style={inputStyle}
                        value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}>
                        <option value="true">true</option>
                        <option value="false">false</option>
                    </select>
                );
            case 'list':
                return (
                    <input ref={this.inputRef as React.RefObject<HTMLInputElement>} type="text" className="form-control" style={inputStyle}
                        title='双击打开窗口编辑列表值'
                        value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}
                        onDoubleClick={this._handleDoubleClick} />
                );
            default:
                return (
                    <input ref={this.inputRef as React.RefObject<HTMLInputElement>} type="text" className="form-control" style={inputStyle}
                        value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur} />
                );
        }
    }

    render() {
        const { rowData, header } = this.props;
        const dateFormat = header.dateFormat;
        let data: unknown = rowData[header.name];
        if (dateFormat) {
            const d = new Date(data as string | number | Date);
            data = formatDate(d, dateFormat);
        }
        if (data && (typeof data === 'object')) {
            data = JSON.stringify(data);
        }
        const styleObj: React.CSSProperties = { marginTop: '5px', minHeight: '26px', wordBreak: 'break-all' };
        return (
            <td style={{ padding: '1px 5px' }}>
                {this.state.editing ? (
                    this._renderEditor()
                ) : (
                    <div style={styleObj} onClick={this._startEdit}>
                        {header.formatter ? header.formatter(data) : data as React.ReactNode}
                    </div>
                )}
            </td>
        );
    }
}
