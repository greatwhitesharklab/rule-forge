import React, {Component} from 'react';
import * as componentEvent from '../componentEvent.js';
import {formatDate} from '../../../Utils.js';
/* bootbox is a global */

export default class Cell extends Component {
    constructor(props) {
        super(props);
        this.state = {editing: false, editorValue: ''};
        this.inputRef = React.createRef();
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.state.editing && !prevState.editing && this.inputRef.current) {
            this.inputRef.current.focus();
        }
    }

    _startEdit = () => {
        const {rowData, header} = this.props;
        if (!header.editable) return;

        if (rowData._editorType) {
            this.setState({editing: true, editorValue: rowData[header.name] || ''});
        } else {
            componentEvent.eventEmitter.emit(componentEvent.SHOW_CELL_EDITOR, {
                rowData,
                colId: header.id
            });
        }
    };

    _handleBlur = (e) => {
        const {rowData, header} = this.props;
        const value = e.target.value;
        rowData[header.name] = value;
        this.setState({editing: false, editorValue: ''});
    };

    _handleChange = (e) => {
        this.setState({editorValue: e.target.value});
    };

    _handleDoubleClick = () => {
        const {rowData, header} = this.props;
        const colName = header.name;
        let editorValue = rowData[colName];
        if (!editorValue || editorValue === '') {
            editorValue = '{}';
        }
        let cellJsonData = JSON.parse(editorValue) || {};
        let rows = cellJsonData.rows || [], targetType = cellJsonData.type;
        const callback = function (rows) {
            const jsonData = {type: targetType, rows};
            const data = JSON.stringify(jsonData);
            rowData[colName] = data;
        };
        if (!targetType) {
            const simulatorCategoryData = window.simulatorCategoryData || [];
            const categorySelect = document.createElement('select');
            categorySelect.className = 'form-control';
            for (let category of simulatorCategoryData) {
                const option = document.createElement('option');
                option.value = category.clazz;
                option.textContent = category.name;
                categorySelect.appendChild(option);
            }
            const emptyOption = document.createElement('option');
            emptyOption.value = '';
            emptyOption.selected = true;
            categorySelect.appendChild(emptyOption);
            window.bootbox.dialog('选择子对象类型', categorySelect, function () {
                targetType = categorySelect.value;
                if (!targetType || targetType === '') {
                    window.bootbox.alert('请先选择子对象类型!');
                    return;
                }
                let categoryTarget = null;
                for (let category of simulatorCategoryData) {
                    if (targetType === category.clazz) {
                        categoryTarget = category;
                        break;
                    }
                }
                const variables = categoryTarget.variables || [];
                componentEvent.eventEmitter.emit(componentEvent.SHOW_CHILD_LIST_DIALOG, {
                    variables,
                    rows,
                    callback
                });
            });
        } else {
            const simulatorCategoryData = window.simulatorCategoryData || [];
            let categoryTarget = null;
            for (let category of simulatorCategoryData) {
                if (targetType === category.clazz) {
                    categoryTarget = category;
                    break;
                }
            }
            const variables = categoryTarget.variables || [];
            componentEvent.eventEmitter.emit(componentEvent.SHOW_CHILD_LIST_DIALOG, {
                variables,
                rows,
                callback
            });
        }
    };

    _renderEditor() {
        const {rowData} = this.props;
        const {editorValue} = this.state;
        const inputStyle = {height: '31px'};

        switch (rowData._editorType) {
            case 'number':
                return (
                    <input ref={this.inputRef} type="number" className="form-control" style={inputStyle}
                           value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}/>
                );
            case 'date':
                return (
                    <input ref={this.inputRef} type="date" className="form-control" style={inputStyle}
                           value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}/>
                );
            case 'boolean':
                return (
                    <select ref={this.inputRef} className="form-control" style={inputStyle}
                            value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}>
                        <option value="true">true</option>
                        <option value="false">false</option>
                    </select>
                );
            case 'list':
                return (
                    <input ref={this.inputRef} type="text" className="form-control" style={inputStyle}
                           title='双击打开窗口编辑列表值'
                           value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}
                           onDoubleClick={this._handleDoubleClick}/>
                );
            default:
                return (
                    <input ref={this.inputRef} type="text" className="form-control" style={inputStyle}
                           value={editorValue} onChange={this._handleChange} onBlur={this._handleBlur}/>
                );
        }
    }

    render() {
        const {rowData, header} = this.props;
        const dateFormat = header.dateFormat;
        let data = rowData[header.name];
        if (dateFormat) {
            var d = new Date(data);
            data = formatDate(d, dateFormat);
        }
        if (data && (typeof data === 'object')) {
            data = JSON.stringify(data);
        }
        const styleObj = {marginTop: '5px', minHeight: '26px', wordBreak: 'break-all'};
        return (
            <td style={{padding: '1px 5px'}}>
                {this.state.editing ? (
                    this._renderEditor()
                ) : (
                    <div style={styleObj} onClick={this._startEdit}>
                        {header.formatter ? header.formatter(data) : data}
                    </div>
                )}
            </td>
        );
    }
}
