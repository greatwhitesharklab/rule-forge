import * as ACTIONS from './action.js';
import datasourceReducer from '@/datasource/reducer';

function ui(state = {activePanel: 'rules', monitoringTab: 'overview'}, action) {
    switch (action.type) {
        case ACTIONS.SET_ACTIVE_PANEL:
            return {...state, activePanel: action.panel};
        case ACTIONS.SET_MONITORING_TAB:
            return {...state, monitoringTab: action.tab};
        default:
            return state;
    }
}

function tree(state={}, action) {
    switch (action.type){
        case ACTIONS.ADD:
            return Object.assign({},state,{data:[...state.data,action.itemData]});
        case ACTIONS.DEL:
            var actionData=action.data;
            // 根据节点的类型决定更新哪个状态
            if(actionData.type === 'publicResource' || actionData.fullPath.startsWith('/public')) {
                console.log('删除操作：更新 publicResource');
                // 更新 publicResource
                var publicResource=Object.assign({},state.publicResource);
                var map=new Map();
                buildDataMap(publicResource,map);
                var target=map.get(actionData.id);
                var targetData=target.data;
                var targetParentChildren=target.parent.children;
                var pos=targetParentChildren.indexOf(targetData);
                targetParentChildren.splice(pos,1);
                return Object.assign({},state,{publicResource});
            } else {
                // 更新 data (项目列表)
                var data=Object.assign({},state.data);
                var map=new Map();
                buildDataMap(data,map);
                var target=map.get(actionData.id);
                if (!target) {
                    return state;
                }
                var targetData=target.data;
                var targetParentChildren=target.parent.children;
                var pos=targetParentChildren.indexOf(targetData);
                targetParentChildren.splice(pos,1);
                return Object.assign({},state,{data});
            }
        case ACTIONS.UPDATE:
            var data=[...state.data];
            data[action.index].name=action.itemData.name;
            return Object.assign({},state,{data});
        case ACTIONS.LOAD_END:
            var data=action.data;
            return Object.assign({},state,{
                data: data,
                publicResource: action.publicResource
            });
        case ACTIONS.FILE_RENAME:
            var data=action.data;
            
            // 根据节点的类型决定更新哪个状态
            if(data.type === 'publicResource' || data.fullPath.startsWith('/public')) {
                // 更新 publicResource
                var newPublicResource=Object.assign({},state.publicResource);
                var dataMap=new Map();
                buildDataMap(newPublicResource,dataMap);
                var targetData=dataMap.get(data.id).data;
                targetData.name=data.name;
                targetData.fullPath=data.fullPath;
                return Object.assign({},state,{publicResource:newPublicResource});
            } else {
                // 更新 data (项目列表)
                var newData=Object.assign({},state.data);
                var dataMap=new Map();
                buildDataMap(newData,dataMap);
                var targetData=dataMap.get(data.id).data;
                targetData.name=data.name;
                targetData.fullPath=data.fullPath;
                return Object.assign({},state,{data:newData});
            }
        case ACTIONS.CREATE_NEW_PROJECT:
            var parentNodeData=action.parentNodeData;
            
            // 根据父节点的类型决定更新哪个状态
            if(parentNodeData.type === 'publicResource' || parentNodeData.fullPath.startsWith('/public')) {
                // 更新 publicResource
                var newPublicResource=Object.assign({},state.publicResource);
                var dataMap=new Map();
                buildDataMap(newPublicResource,dataMap);
                var targetParentNodeData=dataMap.get(parentNodeData.id);
                if(!targetParentNodeData.data.children){
                    targetParentNodeData.data.children=[];
                }
                targetParentNodeData.data.children.push(action.newProjectData);
                return Object.assign({},state,{publicResource:newPublicResource});
            } else {
                // 更新 data (项目列表)
                var newData=Object.assign({},state.data);
                var dataMap=new Map();
                buildDataMap(newData,dataMap);
                var targetParentNodeData=dataMap.get(parentNodeData.id);
                if(!targetParentNodeData.data.children){
                    targetParentNodeData.data.children=[];
                }
                targetParentNodeData.data.children.push(action.newProjectData);
                return Object.assign({},state,{data:newData});
            }
        case ACTIONS.CREATE_NEW_FILE:
            var parentNodeData=action.parentNodeData;
            
            console.log('CREATE_NEW_FILE reducer 被调用:', {
                parentType: parentNodeData.type,
                parentPath: parentNodeData.fullPath,
                parentId: parentNodeData.id
            });
            
            // 根据父节点的类型决定更新哪个状态
            if(parentNodeData.type === 'publicResource' || parentNodeData.fullPath.startsWith('/public')) {
                console.log('更新 publicResource');
                // 更新 publicResource
                var newPublicResource=Object.assign({},state.publicResource);
                var dataMap=new Map();
                buildDataMap(newPublicResource,dataMap);
                var targetParentNodeData=dataMap.get(parentNodeData.id);
                
                if (!targetParentNodeData) {
                    console.log('在 publicResource 中未找到父节点');
                    return state;
                }
                
                var children=targetParentNodeData.data.children;
                if(!children){
                    children=[];
                    targetParentNodeData.data.children=children;
                }
                children.push(action.newFileData);
                return Object.assign({},state,{publicResource:newPublicResource});
            } else {
                console.log('更新 data (项目列表)');
                // 更新 data (项目列表)
                var newData=Object.assign({},state.data);
                var dataMap=new Map();
                buildDataMap(newData,dataMap);
                
                console.log('dataMap 中的所有节点ID:', Array.from(dataMap.keys()));
                console.log('查找的父节点ID:', parentNodeData.id);
                
                var targetParentNodeData=dataMap.get(parentNodeData.id);
                
                if (!targetParentNodeData) {
                    console.log('在 data 中未找到父节点');
                    return state;
                }
                
                console.log('找到的父节点数据:', targetParentNodeData);
                console.log('父节点的 data 属性:', targetParentNodeData.data);
                
                var children=targetParentNodeData.data.children;
                if(!children){
                    children=[];
                    targetParentNodeData.data.children=children;
                }
                console.log('要添加的新文件数据:', action.newFileData);
                
                // 过滤掉 undefined 元素
                targetParentNodeData.data.children = targetParentNodeData.data.children.filter(child => child !== undefined);
                
                // 直接添加到父节点的 children 数组
                targetParentNodeData.data.children.push(action.newFileData);
                console.log('添加新文件后的父节点 children:', targetParentNodeData.data.children);
                return Object.assign({},state,{data:newData});
            }
        case ACTIONS.LOAD_CHILDREN_END:
            var parentNodeData=action.parentNodeData;
            var childrenData=action.childrenData;
            
            // 根据父节点的类型决定更新哪个状态
            if(parentNodeData.type === 'publicResource' || parentNodeData.fullPath.startsWith('/public')) {
                // 更新 publicResource
                var newPublicResource=Object.assign({},state.publicResource);
                var dataMap=new Map();
                buildDataMap(newPublicResource,dataMap);
                var targetParentNodeData=dataMap.get(parentNodeData.id);
                
                if(targetParentNodeData && targetParentNodeData.data){
                    targetParentNodeData.data.children=childrenData;
                    targetParentNodeData.data._childrenLoaded = true;
                    return Object.assign({},state,{publicResource:newPublicResource});
                }
            } else {
                // 更新 data (项目列表)
                var newData=Object.assign({},state.data);
                var dataMap=new Map();
                buildDataMap(newData,dataMap);
                var targetParentNodeData=dataMap.get(parentNodeData.id);
                
                if(targetParentNodeData && targetParentNodeData.data){
                    targetParentNodeData.data.children=childrenData;
                    targetParentNodeData.data._childrenLoaded = true;
                    return Object.assign({},state,{data:newData});
                }
            }
            return state;

        default:
            return state;
    }
};

function buildDataMap(data,map,cleanActive,parent) {
    if(data instanceof Array){
        data.forEach((item,index)=>{
            map.set(item.id,{parent,data:item});
            if(cleanActive){
                item.active=false;
            }
            const children=item.children;
            if(children){
                buildDataMap(children,map,cleanActive,item);
            }
        });
    }else{
        map.set(data.id,{data,parent});
        if(cleanActive){
            data.active=false;
        }
        const children=data.children;
        if(children){
            buildDataMap(children,map,cleanActive,data);
        }
    }
}

export default function rootReducer(state = {}, action) {
    const treeState = tree(state, action);
    const uiState = ui(state.ui, action);
    const datasourceState = datasourceReducer(state.datasource, action);
    return {...treeState, ui: uiState, datasource: datasourceState};
}