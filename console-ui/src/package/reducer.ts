import * as ACTIONS from './action.js';
import {combineReducers} from 'redux';
import {
    applyNewVersion,
    PackageAction,
    ResourcePackage,
    PackageConfig,
    ResourceItem
} from './action.js';

interface MasterState {
    data?: ResourcePackage[];
}

interface SlaveState {
    data?: ResourcePackage;
}

interface ConfigState {
    data?: PackageConfig;
}

export interface PackageState {
    master: MasterState;
    slave: SlaveState;
    config: ConfigState;
}

function master(state: MasterState = {}, action: PackageAction): MasterState {
    switch (action.type) {
        case ACTIONS.LOAD_MASTER_COMPLETED:
            return Object.assign({}, {}, {data: (action as { masterData: ResourcePackage[] }).masterData});
        case ACTIONS.DEL_MASTER: {
            // 知识包 删除
            const rowIndex = (action as { rowIndex: number }).rowIndex;
            const newData = [...(state.data || [])];
            newData.splice(rowIndex, 1);
            return Object.assign({}, {}, {data: newData});
        }
        case ACTIONS.ADD_MASTER: {
            // 知识包 新增
            let newData: ResourcePackage[] = [];
            if (state.data) {
                newData = [...state.data];
            }
            const newPackage = (action as { data: ResourcePackage }).data;
            let error: string | null = null;
            newData.forEach((p) => {
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
            return Object.assign({}, {}, {data: newData});
        }
        case ACTIONS.UPDATE_MASTER: {
            // 知识包 保存
            const newData = [...(state.data || [])];
            const data = (action as { data: { rowIndex: number; packageName: string } }).data;
            const targetPackage = newData[data.rowIndex];
            targetPackage.name = data.packageName;
            return Object.assign({}, {}, {data: newData});
        }
        case ACTIONS.SAVE:
            ACTIONS.saveData(
                state.data || [],
                (action as { newVersion: boolean }).newVersion,
                (action as { project: string }).project,
                (action as { associatedFiles: string[] }).associatedFiles,
                (action as { versionComment: string }).versionComment,
                (action as { packageId: string }).packageId,
                (action as { callback?: () => void }).callback
            );

            return state;
        case ACTIONS.APPLY:
            applyNewVersion(
                state.data || [],
                (action as { project: string }).project,
                (action as { packageConfig: PackageConfig }).packageConfig,
                (action as { currentPackage: ResourcePackage }).currentPackage
            );
            return state;
        default:
            return state;
    }
}

function slave(state: SlaveState = {}, action: PackageAction): SlaveState {
    switch (action.type) {
        case ACTIONS.LOAD_SLAVE_COMPLETE:
            return Object.assign({}, {}, {data: (action as { masterRowData: ResourcePackage }).masterRowData});
        case ACTIONS.DEL_SLAVE: {
            // 知识包 删除文件
            const rowIndex = (action as { rowIndex: number }).rowIndex;
            const newData = Object.assign({}, state.data) as ResourcePackage;
            newData.resourceItems.splice(rowIndex, 1);
            return Object.assign({}, {}, {data: newData});
        }
        case ACTIONS.ADD_SLAVE: {
            // 知识包 添加文件
            const newData = Object.assign({}, state.data) as ResourcePackage;
            const data = (action as { data: ResourceItem }).data;
            newData.resourceItems.push(data);
            return Object.assign({}, {}, {data: newData});
        }
        case ACTIONS.UPDATE_SLAVE: {
            // 知识包 更新文件
            const newData = Object.assign({}, state.data) as ResourcePackage;
            const data = (action as { data: ResourceItem & { rowIndex: number } }).data;
            const rowIndex = data.rowIndex;
            const itemData = newData.resourceItems[rowIndex];
            itemData.name = data.name;
            itemData.path = data.path;
            itemData.version = data.version;
            return Object.assign({}, {}, {data: newData});
        }
        default:
            return state;
    }
}

function config(state: ConfigState = {}, action: PackageAction): ConfigState {
    switch (action.type) {
        case ACTIONS.LOAD_PACKAGE_CONFIG_COMPLETE:
            return Object.assign({}, {}, {data: (action as { config: PackageConfig }).config});
        default:
            return state;
    }
}

export default combineReducers<PackageState>({master, slave, config} as any);
