/**
 * Agent action types and creators
 */

import {httpGet, jsonPost, httpDelete} from '../api/client.js';

// Action types
export const SET_SESSIONS = 'agent_set_sessions';
export const SET_ACTIVE_SESSION = 'agent_set_active_session';
export const SET_MESSAGES = 'agent_set_messages';
export const ADD_MESSAGE = 'agent_add_message';
export const SET_LOADING = 'agent_set_loading';
export const SET_STREAMING = 'agent_set_streaming';
export const SET_CONFIG = 'agent_set_config';
export const SET_STATUS = 'agent_set_status';
export const SET_TOOL_STATUS = 'agent_set_tool_status';

// Interfaces
export interface AgentSession {
    id: string;
    title: string;
    project: string | null;
    createdBy: string;
    createTime: string;
    updateTime: string;
}

export interface AgentMessage {
    id: string;
    sessionId: string;
    role: 'system' | 'user' | 'assistant' | 'tool' | 'streaming';
    content: string;
    toolCallId: string | null;
    toolName: string | null;
    createTime: string;
}

export interface AgentStatus {
    available: boolean;
    toolsCount: number;
    vendor: string;
    model: string;
}

export interface ToolStatus {
    tool: string;
    args?: string;
    result?: string;
    loading: boolean;
}

// Action creators
export function loadSessions(): (dispatch: Function) => Promise<void> {
    return async (dispatch: Function) => {
        try {
            const sessions: AgentSession[] = await httpGet<AgentSession[]>('/agent/sessions', {silent: true});
            if (Array.isArray(sessions)) {
                dispatch({type: SET_SESSIONS, payload: sessions});
            }
        } catch (e) {
            console.error('Failed to load sessions', e);
        }
    };
}

export function createSession(title?: string, project?: string): (dispatch: Function) => Promise<AgentSession | null> {
    return async (dispatch: Function) => {
        try {
            const session: AgentSession = await jsonPost<AgentSession>('/agent/sessions', {
                title: title || '新对话',
                project: project || null,
            });
            dispatch(loadSessions());
            dispatch({type: SET_ACTIVE_SESSION, payload: session.id});
            return session;
        } catch (e) {
            console.error('Failed to create session', e);
            return null;
        }
    };
}

export function loadMessages(sessionId: string): (dispatch: Function) => Promise<void> {
    return async (dispatch: Function) => {
        try {
            const messages: AgentMessage[] = await httpGet<AgentMessage[]>('/agent/sessions/' + sessionId + '/messages', {silent: true});
            dispatch({type: SET_MESSAGES, payload: messages});
        } catch (e) {
            console.error('Failed to load messages', e);
        }
    };
}

export function sendMessage(sessionId: string, message: string): (dispatch: Function) => Promise<void> {
    return async (dispatch: Function) => {
        dispatch({type: SET_STREAMING, payload: true});
        dispatch({type: ADD_MESSAGE, payload: {role: 'user', content: message, createTime: new Date().toISOString()}});
        dispatch({type: SET_LOADING, payload: true});

        try {
            const resp = await fetch(window._server + '/agent/chat', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({sessionId, message})
            });

            if (!resp.ok || !resp.body) {
                throw new Error('Chat request failed');
            }

            // Read SSE stream
            const reader = resp.body.getReader();
            const decoder = new TextDecoder();
            let assistantContent = '';

            while (true) {
                const {done, value} = await reader.read();
                if (done) break;

                const text = decoder.decode(value, {stream: true});
                // Parse SSE format
                const lines = text.split('\n');
                for (const line of lines) {
                    if (line.startsWith('event:')) continue;
                    if (line.startsWith('data:')) {
                        const data = line.substring(5).trim();
                        if (data === '[DONE]') continue;

                        // Try to parse as JSON for tool events
                        try {
                            const parsed = JSON.parse(data);
                            if (parsed.tool) {
                                dispatch({type: SET_TOOL_STATUS, payload: {tool: parsed.tool, loading: true, args: parsed.args}});
                            }
                        } catch {
                            // Plain token text
                            assistantContent += data;
                            dispatch({type: ADD_MESSAGE, payload: {
                                role: 'streaming', content: assistantContent, createTime: new Date().toISOString()
                            }});
                        }
                    }
                }
            }

            // Finalize
            dispatch({type: ADD_MESSAGE, payload: {
                role: 'assistant', content: assistantContent, createTime: new Date().toISOString()
            }});
        } catch (e) {
            console.error('Chat error', e);
            dispatch({type: ADD_MESSAGE, payload: {
                role: 'assistant', content: '抱歉，发生了错误：' + (e as Error).message,
                createTime: new Date().toISOString()
            }});
        } finally {
            dispatch({type: SET_LOADING, payload: false});
            dispatch({type: SET_STREAMING, payload: false});
        }
    };
}

export function deleteSession(sessionId: string): (dispatch: Function) => Promise<void> {
    return async (dispatch: Function) => {
        try {
            await httpDelete('/agent/sessions/' + sessionId, {silent: true});
            dispatch(loadSessions());
            dispatch({type: SET_ACTIVE_SESSION, payload: null});
        } catch (e) {
            console.error('Failed to delete session', e);
        }
    };
}

export function loadConfig(): (dispatch: Function) => Promise<void> {
    return async (dispatch: Function) => {
        try {
            const config = await httpGet('/agent/config', {silent: true});
            dispatch({type: SET_CONFIG, payload: config});
        } catch (e) {
            console.error('Failed to load config', e);
        }
    };
}

export function loadStatus(): (dispatch: Function) => Promise<void> {
    return async (dispatch: Function) => {
        try {
            const status: AgentStatus = await httpGet<AgentStatus>('/agent/status', {silent: true});
            dispatch({type: SET_STATUS, payload: status});
        } catch (e) {
            dispatch({type: SET_STATUS, payload: {available: false, toolsCount: 0, vendor: '', model: ''}});
        }
    };
}
