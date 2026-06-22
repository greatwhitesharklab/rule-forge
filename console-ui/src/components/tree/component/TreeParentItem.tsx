import React, {Component} from 'react';
import TreeItem from './TreeItem';

interface TreeParentItemProps {
    children: TreeNodeData[];
    dispatch?: (action: unknown) => void;
    selectDir?: (data: TreeNodeData) => void;
    expandLevel?: number;
    treeType?: string;
    /** V6.13.1:透传到子 TreeItem */
    readOnly?: boolean;
    /** V6.13.1:透传到子 TreeItem */
    onFileReadOnlyClick?: (data: TreeNodeData) => void;
}

export default class TreeParentItem extends Component<TreeParentItemProps> {
    render() {
        const {children, dispatch, selectDir, treeType, readOnly, onFileReadOnlyClick} = this.props;
        const result: React.ReactElement[] = [];
        children.forEach((item, index) => {
            result.push(
                <TreeItem data={item} key={(item.fullPath ? item.fullPath + '_' + (item.type || '') : item.id) || ('tree_item_' + index)} dispatch={dispatch} selectDir={selectDir} expandLevel={this.props.expandLevel} treeType={treeType} readOnly={readOnly} onFileReadOnlyClick={onFileReadOnlyClick}/>
            );
        });
        return (
            <ul style={{marginLeft: "-18px"}}>
                {result}
            </ul>
        );
    }
}
