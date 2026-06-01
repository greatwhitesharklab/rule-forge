import React, {Component} from 'react';
import TreeItem from './TreeItem';

interface TreeParentItemProps {
    children: TreeNodeData[];
    dispatch?: (action: unknown) => void;
    selectDir?: (data: TreeNodeData) => void;
    expandLevel?: number;
    treeType?: string;
}

export default class TreeParentItem extends Component<TreeParentItemProps> {
    render() {
        const {children, dispatch, selectDir, treeType} = this.props;
        const result: React.ReactElement[] = [];
        children.forEach((item, index) => {
            result.push(
                <TreeItem data={item} key={(item.fullPath ? item.fullPath + '_' + (item.type || '') : item.id) || ('tree_item_' + index)} dispatch={dispatch} selectDir={selectDir} expandLevel={this.props.expandLevel} treeType={treeType}/>
            );
        });
        return (
            <ul style={{marginLeft: "-18px"}}>
                {result}
            </ul>
        );
    }
}
