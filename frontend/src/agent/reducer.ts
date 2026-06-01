import {
    SET_SESSIONS, SET_ACTIVE_SESSION, SET_MESSAGES, ADD_MESSAGE,
    SET_LOADING, SET_STREAMING, SET_CONFIG, SET_STATUS, SET_TOOL_STATUS,
    AgentSession, AgentMessage, AgentStatus, ToolStatus
} from './action';

export interface AgentState {
    sessions: AgentSession[];
    activeSessionId: string | null;
    messages: AgentMessage[];
    loading: boolean;
    streaming: boolean;
    config: Record<string, string>;
    status: AgentStatus | null;
    toolStatus: ToolStatus | null;
}

const initialState: AgentState = {
    sessions: [],
    activeSessionId: null,
    messages: [],
    loading: false,
    streaming: false,
    config: {},
    status: null,
    toolStatus: null,
};

type AgentAction = {
    type: string;
    payload?: unknown;
};

export default function reducer(state: AgentState = initialState, action: AgentAction): AgentState {
    switch (action.type) {
        case SET_SESSIONS:
            return {...state, sessions: action.payload as AgentSession[]};
        case SET_ACTIVE_SESSION:
            return {...state, activeSessionId: action.payload as string | null};
        case SET_MESSAGES:
            return {...state, messages: action.payload as AgentMessage[]};
        case ADD_MESSAGE: {
            const msg = action.payload as AgentMessage;
            // Replace last streaming message or append
            const last = state.messages[state.messages.length - 1];
            if (last && last.role === 'streaming') {
                return {...state, messages: [...state.messages.slice(0, -1), msg]};
            }
            return {...state, messages: [...state.messages, msg]};
        }
        case SET_LOADING:
            return {...state, loading: action.payload as boolean};
        case SET_STREAMING:
            return {...state, streaming: action.payload as boolean};
        case SET_CONFIG:
            return {...state, config: action.payload as Record<string, string>};
        case SET_STATUS:
            return {...state, status: action.payload as AgentStatus};
        case SET_TOOL_STATUS:
            return {...state, toolStatus: action.payload as ToolStatus};
        default:
            return state;
    }
}
