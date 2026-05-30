import {
    LOAD_DATASOURCES, LOAD_DATASOURCES_COMPLETED,
    LOAD_ENTITY_MAPPINGS, LOAD_ENTITY_MAPPINGS_COMPLETED,
    LOAD_FIELD_MAPPINGS, LOAD_FIELD_MAPPINGS_COMPLETED,
    SET_SELECTED_DATASOURCE, SET_TAB
} from './action';

const initialState = {
    datasources: [],
    entityMappings: [],
    fieldMappings: [],
    selectedDatasource: null,
    activeTab: 'datasources',  // datasources | mappings
    loading: false
};

export default function (state = initialState, action) {
    switch (action.type) {
        case LOAD_DATASOURCES:
            return {...state, loading: true};
        case LOAD_DATASOURCES_COMPLETED:
            return {...state, datasources: action.data || [], loading: false};
        case LOAD_ENTITY_MAPPINGS:
            return {...state, loading: true};
        case LOAD_ENTITY_MAPPINGS_COMPLETED:
            return {...state, entityMappings: action.data || [], loading: false};
        case LOAD_FIELD_MAPPINGS:
            return {...state, loading: true};
        case LOAD_FIELD_MAPPINGS_COMPLETED:
            return {...state, fieldMappings: action.data || [], loading: false};
        case SET_SELECTED_DATASOURCE:
            return {...state, selectedDatasource: action.data};
        case SET_TAB:
            return {...state, activeTab: action.data};
        default:
            return state;
    }
}
