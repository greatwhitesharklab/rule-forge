import '../css/grid.css';
import { Component, createRef } from 'react';
import Cell from './Cell.tsx';
import { uniqueID } from '../../componentAction.js';
import type { GridColumnHeader } from './CellEditor.tsx';

interface OperationDef {
    label: string;
    icon?: string;
    style?: React.CSSProperties;
    click: (rowIndex: number, rowData: Record<string, unknown>) => void;
}

interface RowProps {
    headers: GridColumnHeader[];
    rowData: Record<string, unknown>;
    rowIndex: number;
    operations?: OperationDef[];
    select?: boolean;
    dispatch?: unknown;
    rowClick?: (rowData: Record<string, unknown>, rowIndex: number) => void;
    ready?: (rowIndex: number, rowData: Record<string, unknown>) => void;
}

export default class Row extends Component<RowProps> {
    private trRef = createRef<HTMLTableRowElement>();

    _handleClick = () => {
        const { rowData, rowIndex, rowClick } = this.props;
        if (rowClick) {
            rowClick(rowData, rowIndex);
        }
        const tr = this.trRef.current;
        if (tr) {
            const siblings = tr.parentElement!.children;
            for (let i = 0; i < siblings.length; i++) {
                siblings[i].classList.remove('bg-warning');
            }
            tr.classList.add('bg-warning');
        }
    };

    render() {
        const { headers, rowData, rowIndex, operations, select } = this.props;
        const tds: React.ReactNode[] = [];
        headers.forEach((header) => {
            tds.push(
                <Cell key={uniqueID()} onchange={(newValue: string) => {
                    rowData[header.name] = newValue;
                }} rowData={rowData} header={header} />
            );
        });
        if (operations) {
            tds.push(
                <td key={uniqueID()} style={{ padding: "5px 5px" }}>
                    {
                        operations.map((op, index) => {
                            if (op.icon) {
                                return (
                                    <i key={uniqueID()} className={op.icon} title={op.label} style={op.style}
                                        onClick={op.click.bind(this, rowIndex, rowData)} />
                                );
                            } else {
                                return (
                                    <button key={uniqueID()} type="button" className="btn btn-link"
                                        style={{ padding: '0px 1px' }}
                                        onClick={op.click.bind(this, rowIndex, rowData)}>{op.label}
                                    </button>
                                );
                            }
                        })
                    }
                </td>
            );
        }
        let trClass = select ? 'bg-warning' : '';
        trClass += ' content-tr';
        return (
            <tr ref={this.trRef} style={{ height: '26px' }} className={trClass} onClick={this._handleClick}>
                {tds}
            </tr>
        );
    }
}
