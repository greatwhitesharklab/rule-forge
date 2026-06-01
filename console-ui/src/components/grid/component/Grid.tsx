import '../css/grid.css';
import { Component } from 'react';
import Row from './Row.tsx';
import CellEditor from './CellEditor.tsx';
import { uniqueID } from '../../componentAction.js';
import type { GridColumnHeader } from './CellEditor.tsx';

interface OperationDef {
    label: string;
    icon?: string;
    style?: React.CSSProperties;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    click: (rowIndex: number, rowData: any) => void;
}

interface OperationConfig {
    width?: string;
    operations: OperationDef[];
}

interface GridProps {
    headers: GridColumnHeader[];
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rows?: any[];
    operationConfig?: OperationConfig;
    dispatch?: unknown;
    selectFirst?: boolean;
    uniqueKey?: boolean;
    width?: string;
    onchange?: (value: string) => void;
    onblur?: (e: React.FocusEvent<HTMLElement>) => void;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rowClick?: (rowData: any, rowIndex: number) => void;
    ready?: (rowIndex: number, rowData: Record<string, unknown>) => void;
}

interface GridState {
    display: string;
    filterTexts: Record<string, string>;
}

class Grid extends Component<GridProps, GridState> {
    constructor(props: GridProps) {
        super(props);
        this.state = { display: 'none', filterTexts: {} };
    }

    onFilter(colIndex: number, e: React.KeyboardEvent<HTMLInputElement>) {
        if (e.key !== 'Enter') {
            return;
        }
        const value = e.currentTarget.value;
        const name = e.currentTarget.name;
        const oldData = this.state.filterTexts[name];
        if (value === oldData) {
            return;
        }
        this.setState(prevState => ({
            filterTexts: { ...prevState.filterTexts, [name]: value }
        }));
    }

    _matchesFilter(rowData: Record<string, unknown>, headers: GridColumnHeader[]): boolean {
        const filterTexts = this.state.filterTexts;
        const filterNames = Object.keys(filterTexts);
        for (let i = 0; i < filterNames.length; i++) {
            const filterName = filterNames[i];
            const filterValue = filterTexts[filterName];
            if (!filterValue) continue;
            const header = headers.find(h => h.id === filterName);
            if (!header) continue;
            let cellValue = rowData[header.name];
            if (cellValue && typeof cellValue === 'object') {
                cellValue = JSON.stringify(cellValue);
            }
            const cellStr = cellValue != null ? String(cellValue) : '';
            if (cellStr.indexOf(filterValue) === -1) {
                return false;
            }
        }
        return true;
    }

    render() {
        const { headers, operationConfig, dispatch, selectFirst } = this.props;
        const rows = this.props.rows || [];
        const headerContent: React.ReactNode[] = [];
        const bodyContent: React.ReactNode[] = [];
        headers.forEach((header) => {
            if (header.editable) {
                headerContent.push(
                    <td key={uniqueID()} style={{ width: header.width }}>
                        <label>{header.label}</label>
                        <CellEditor onchange={this.props.onchange} onblur={(e: React.FocusEvent<HTMLElement>) => {
                            this.setState({ display: 'none' });
                            this.props.onblur && this.props.onblur(e);
                        }} header={header} display={this.state.display} />
                    </td>
                );
            } else {
                headerContent.push(
                    <td key={uniqueID()} style={{ width: header.width }}>
                        <label>{header.label}</label>
                    </td>
                );
            }
        });
        if (operationConfig) {
            headerContent.push(
                <td key={uniqueID()} style={{ width: operationConfig.width }}><label>操作列</label></td>
            );
        }
        const filterRow = (
            <tr key='filterrow' style={{ background: 'var(--rf-bg-base)' }}>
                {headers.map((header, index) => {
                    if (header.filterable) {
                        return (<td key={uniqueID()}>
                            <input type="text" onKeyPress={this.onFilter.bind(this, index)} name={header.id}
                                className="form-control" style={{ height: '28px' }}
                                placeholder='请输入过滤条件，回车查询...' />
                        </td>);
                    } else if (!header.hideFilterRow) {
                        return (<td key={uniqueID()}>&nbsp;</td>);
                    }
                    return null;
                })}
                {operationConfig ? (<td></td>) : null}
            </tr>
        );
        bodyContent.push(filterRow);
        rows.forEach((row, rowIndex) => {
            if (!row.id) {
                row.id = uniqueID();
            }
            if (!this._matchesFilter(row, headers)) {
                return;
            }
            const rowKey = row.id as string;
            if (rowIndex === 0 && selectFirst) {
                bodyContent.push(
                    <Row key={rowKey} select={true} ready={this.props.ready} headers={headers} dispatch={dispatch}
                        rowClick={this.props.rowClick} operations={operationConfig ? operationConfig.operations : null}
                        rowData={row} rowIndex={rowIndex} />
                );
            } else {
                bodyContent.push(
                    <Row key={rowKey} ready={this.props.ready} headers={headers} dispatch={dispatch}
                        rowClick={this.props.rowClick} operations={operationConfig ? operationConfig.operations : null}
                        rowData={row} rowIndex={rowIndex} />
                );
            }
        });
        const tableStyle: React.CSSProperties = { margin: 0, width: (this.props.width ? this.props.width : '100%') };
        return (
            <table className="table table-bordered" style={tableStyle}>
                <thead>
                    <tr className="well">{headerContent}</tr>
                </thead>
                <tbody>{bodyContent}</tbody>
            </table>
        );
    }
}

export default Grid;
