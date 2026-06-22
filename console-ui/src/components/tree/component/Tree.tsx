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
    /** V6.13.1:只读模式 (看 git 历史版本),见 TreeItem.readOnly 注释 */
    readOnly?: boolean;
    /** V6.13.1:readOnly 模式下文件 click 回调,代替 window.open 编辑器 */
    onFileReadOnlyClick?: (data: TreeNodeData) => void;
}

class Tree extends Component<TreeProps> {
    static defaultProps = {expandLevel: 3};

    render() {
        const {data, dispatch, draggable, treeType, readOnly, onFileReadOnlyClick} = this.props;
        if (data) {
            // Render children directly, skip the root node itself
            const items = data.children || [];
            return (
                <div className="tree">
                    <ul>
                        {items.map((child, index) => (
                            <TreeItem key={child.id || (child.fullPath + '_' + index)} data={child} dispatch={dispatch} treeType={treeType}
                                      expandLevel={this.props.expandLevel} draggable={draggable}
                                      readOnly={readOnly} onFileReadOnlyClick={onFileReadOnlyClick}/>
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
