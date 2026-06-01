import {
    LOAD_DATASOURCES, LOAD_DATASOURCES_COMPLETED,
    LOAD_ENTITY_MAPPINGS, LOAD_ENTITY_MAPPINGS_COMPLETED,
    LOAD_FIELD_MAPPINGS, LOAD_FIELD_MAPPINGS_COMPLETED,
    SET_SELECTED_DATASOURCE, SET_TAB,
    DatasourceItem, EntityMapping, FieldMapping
} from './action';

interface DatasourceState {
    datasources: DatasourceItem[];
    entityMappings: EntityMapping[];
    fieldMappings: FieldMapping[];
    selectedDatasource: { id: number; clazz: string } | null;
    activeTab: string;
    loading: boolean;
}

const initialState: DatasourceState = {
    datasources: [],
    entityMappings: [],
    fieldMappings: [],
    selectedDatasource: null,
    activeTab: 'datasources',
    loading: false
};

export default function (state: DatasourceState = initialState, action: { type: string; data?: unknown }): DatasourceState {
    switch (action.type) {
        case LOAD_DATASOURCES:
            return {...state, loading: true};
        case LOAD_DATASOURCES_COMPLETED:
            return {...state, datasources: (action.data as DatasourceItem[]) || [], loading: false};
        case LOAD_ENTITY_MAPPINGS:
            return {...state, loading: true};
        case LOAD_ENTITY_MAPPINGS_COMPLETED:
            return {...state, entityMappings: (action.data as EntityMapping[]) || [], loading: false};
        case LOAD_FIELD_MAPPINGS:
            return {...state, loading: true};
        case LOAD_FIELD_MAPPINGS_COMPLETED:
            return {...state, fieldMappings: (action.data as FieldMapping[]) || [], loading: false};
        case SET_SELECTED_DATASOURCE:
            return {...state, selectedDatasource: action.data as { id: number; clazz: string }};
        case SET_TAB:
            return {...state, activeTab: action.data as string};
        default:
            return state;
    }
}
