import '../css/tree.css';
import '../../../css/iconfont.css';
import TreeItem from './TreeItem';
import React, {Component} from 'react';
import {connect} from 'react-redux';

interface TreeProps {
    treeType?: string;
    expandLevel?: number;
    draggable?: boolean;
    // Mapped from Redux state
    data?: TreeNodeData;
    dispatch?: (action: unknown) => void;
}

class Tree extends Component<TreeProps> {
    static defaultProps = {expandLevel: 3};

    render() {
        const {data, dispatch, draggable, treeType} = this.props;
        if (data) {
            // Render children directly, skip the root node itself
            const items = data.children || [];
            return (
                <div className="tree">
                    <ul>
                        {items.map((child, index) => (
                            <TreeItem key={child.id || (child.fullPath + '_' + index)} data={child} dispatch={dispatch} treeType={treeType}
                                      expandLevel={this.props.expandLevel} draggable={draggable}/>
                        ))}
                    </ul>
                </div>
            );
        } else {
            return (<div className="tree"><ul/></div>);
        }
    }
}

interface RootStateLike {
    publicResource?: TreeNodeData;
    data?: TreeNodeData;
    [key: string]: unknown;
}

function selector(state: RootStateLike, ownProps: TreeProps) {
    return {
        data: ownProps.treeType === 'public' ? state.publicResource : state.data
    };
}

export default connect(selector)(Tree);
