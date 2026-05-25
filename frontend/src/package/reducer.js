import * as ACTIONS from './action.js';
import {combineReducers} from 'redux';
import {applyNewVersion} from "./action.js";

function master(state = {}, action) {
    switch (action.type) {
        case ACTIONS.LOAD_MASTER_COMPLETED:
            return Object.assign({}, state.prototype, {data: action.masterData});
        case ACTIONS.DEL_MASTER:
            // 知识包 删除
            var rowIndex = action.rowIndex;
            var newData = [...state.data];
            newData.splice(rowIndex, 1);
            return Object.assign({}, state.prototype, {data: newData});
        case ACTIONS.ADD_MASTER:
            // 知识包 新增
            var newData = [];
            if (state.data) {
                newData = [...state.data];
            }
            var newPackage = action.data;
            var error = null;
            newData.forEach((p, index) => {
                if (p.id === newPackage.id) {
                    error = '当前包采用的编码已存在，添加失败.';
                    return false;
                }
            });
            if (error) {
                window.bootbox.alert(error);
                return state;
            }
            newPackage.resourceItems = [];
            newPackage.createDate = new Date();
            newData.push(newPackage);
            return Object.assign({}, state.prototype, {data: newData});
        case ACTIONS.UPDATE_MASTER:
            // 知识包 保存
            var newData = [...state.data];
            var data = action.data;
            var targetPackage = newData[data.rowIndex];
            targetPackage.name = data.packageName;
            return Object.assign({}, state.prototype, {data: newData});
        case ACTIONS.SAVE:
            ACTIONS.saveData(state.data, action.newVersion, action.project, action.associatedFiles, action.versionComment, action.packageId, action.callback);

            return state;
        case ACTIONS.APPLY:
            ACTIONS.applyNewVersion(state.data, action.project, action.packageConfig, action.currentPackage);
            return state;
        default:
            return state;
    }
}

function slave(state = {}, action) {
    switch (action.type) {
        case ACTIONS.LOAD_SLAVE_COMPLETE:
            return Object.assign({}, state.prototype, {data: action.masterRowData});
        case ACTIONS.DEL_SLAVE:
            // 知识包 删除文件
            var rowIndex = action.rowIndex;
            var newData = Object.assign({}, state.data);
            newData.resourceItems.splice(rowIndex, 1);
            return Object.assign({}, state.prototype, {data: newData});
        case ACTIONS.ADD_SLAVE:
            // 知识包 添加文件
            var newData = Object.assign({}, state.data);
            var data = action.data;
            newData.resourceItems.push(data);
            return Object.assign({}, state.prototype, {data: newData});
        case ACTIONS.UPDATE_SLAVE:
            // 知识包 更新文件
            var newData = Object.assign({}, state.data);
            var data = action.data;
            var rowIndex = data.rowIndex;
            var itemData = newData.resourceItems[rowIndex];
            itemData.name = data.name;
            itemData.path = data.path;
            itemData.version = data.version;
            return Object.assign({}, state.prototype, {data: newData});
        default:
            return state;
    }
}

function config(state = {}, action) {
    switch (action.type) {
        case ACTIONS.LOAD_PACKAGE_CONFIG_COMPLETE:
            return Object.assign({}, state.prototype, {data: action.config});
        default:
            return state;
    }
}

export default combineReducers({master, slave, config});
