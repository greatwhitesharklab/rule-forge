import '../css/tree.css';
import '../../../css/iconfont.css';
import TreeItem from './TreeItem';
import React, {Component} from 'react';

interface CommonTreeProps {
    data: TreeNodeData;
    selectDir?: (data: TreeNodeData) => void;
    expandLevel?: number;
}

function buildTreeDataLevel(data: TreeNodeData, level: number): void {
    data._level = level++;
    const children = data.children;
    if (children) {
        children.forEach((child) => {
            buildTreeDataLevel(child, level);
        });
    }
}

export default class CommonTree extends Component<CommonTreeProps> {
    static defaultProps = {expandLevel: 3};

    render() {
        const {data, selectDir} = this.props;
        if (data) {
            buildTreeDataLevel(data, 1);
            return (
                <ul style={{paddingLeft: '20px'}}>
                    <TreeItem data={data} selectDir={selectDir} expandLevel={this.props.expandLevel}/>
                </ul>
            );
        } else {
            return (<ul></ul>);
        }
    }
}
