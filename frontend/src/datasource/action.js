export const LOAD_DATASOURCES = 'datasource_load_datasources';
export const LOAD_DATASOURCES_COMPLETED = 'datasource_load_datasources_completed';
export const LOAD_ENTITY_MAPPINGS = 'datasource_load_entity_mappings';
export const LOAD_ENTITY_MAPPINGS_COMPLETED = 'datasource_load_entity_mappings_completed';
export const LOAD_FIELD_MAPPINGS = 'datasource_load_field_mappings';
export const LOAD_FIELD_MAPPINGS_COMPLETED = 'datasource_load_field_mappings_completed';
export const SET_SELECTED_DATASOURCE = 'datasource_set_selected_datasource';
export const SET_TAB = 'datasource_set_tab';

const BASE = window._server + '/datasource';

export function loadDatasources() {
    return function (dispatch) {
        dispatch({type: LOAD_DATASOURCES});
        fetch(BASE)
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then(data => dispatch({type: LOAD_DATASOURCES_COMPLETED, data}))
            .catch(err => {
                console.error('加载数据源列表失败', err);
                dispatch({type: LOAD_DATASOURCES_COMPLETED, data: []});
            });
    };
}

export function createDatasource(datasource) {
    return function (dispatch) {
        fetch(BASE, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(datasource)
        })
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then(() => dispatch(loadDatasources()))
            .catch(err => console.error('创建数据源失败', err));
    };
}

export function updateDatasource(id, datasource) {
    return function (dispatch) {
        fetch(BASE + '/' + id, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(datasource)
        })
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then(() => dispatch(loadDatasources()))
            .catch(err => console.error('更新数据源失败', err));
    };
}

export function deleteDatasource(id) {
    return function (dispatch) {
        fetch(BASE + '/' + id, {method: 'DELETE'})
            .then(resp => {
                if (!resp.ok) throw resp;
            })
            .then(() => dispatch(loadDatasources()))
            .catch(err => console.error('删除数据源失败', err));
    };
}

export function testConnection(id) {
    return function () {
        return fetch(BASE + '/' + id + '/test', {method: 'POST'})
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .catch(err => {
                console.error('测试连接失败', err);
                return {success: false, message: '请求失败'};
            });
    };
}

export function loadEntityMappings() {
    return function (dispatch) {
        dispatch({type: LOAD_ENTITY_MAPPINGS});
        fetch(BASE + '/entity-mapping')
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then(data => dispatch({type: LOAD_ENTITY_MAPPINGS_COMPLETED, data}))
            .catch(err => {
                console.error('加载实体映射失败', err);
                dispatch({type: LOAD_ENTITY_MAPPINGS_COMPLETED, data: []});
            });
    };
}

export function saveEntityMapping(clazz, datasourceId) {
    return function (dispatch) {
        fetch(BASE + '/entity-mapping', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({clazz, datasourceId})
        })
            .then(resp => {
                if (!resp.ok) throw resp;
            })
            .then(() => dispatch(loadEntityMappings()))
            .catch(err => console.error('保存实体映射失败', err));
    };
}

export function loadFieldMappings(datasourceId, clazz) {
    return function (dispatch) {
        dispatch({type: LOAD_FIELD_MAPPINGS});
        fetch(BASE + '/' + datasourceId + '/field-mappings?clazz=' + encodeURIComponent(clazz))
            .then(resp => {
                if (!resp.ok) throw resp;
                return resp.json();
            })
            .then(data => dispatch({type: LOAD_FIELD_MAPPINGS_COMPLETED, data}))
            .catch(err => {
                console.error('加载字段映射失败', err);
                dispatch({type: LOAD_FIELD_MAPPINGS_COMPLETED, data: []});
            });
    };
}

export function setSelectedDatasource(datasource) {
    return {type: SET_SELECTED_DATASOURCE, data: datasource};
}

export function setTab(tab) {
    return {type: SET_TAB, data: tab};
}
