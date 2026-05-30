import React,{Component,PropTypes} from 'react';
import TreeItem from './TreeItem.jsx';

export default class TreeParentItem extends Component{
    render(){
        const {children,dispatch,selectDir,treeType}=this.props;
        let result=[];
        children.forEach((item,index)=>{
            result.push(
                <TreeItem data={item} key={(item.fullPath ? item.fullPath + '_' + (item.type || '') : item.id) || ('tree_item_' + index)} dispatch={dispatch} selectDir={selectDir} expandLevel={this.props.expandLevel} treeType={this.props.treeType}/>
            );
        });
        return (
            <ul style={{marginLeft:"-18px"}}>
                {result}
            </ul>
        );
    }
};