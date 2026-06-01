import {Component} from 'react';
import {createStore} from 'redux';
import {Provider, connect} from 'react-redux';
import reducer from './reducer';
import {
    loadSessions, createSession, loadMessages, sendMessage,
    deleteSession, loadStatus
} from './action';
import ChatPanel from './components/ChatPanel';
import ConfigPanel from './components/ConfigPanel';
import type {AgentSession, AgentMessage} from './action';
import type {AgentState} from './reducer';

interface AgentPanelProps {
    sessions: AgentSession[];
    activeSessionId: string | null;
    messages: AgentMessage[];
    loading: boolean;
    streaming: boolean;
    status: AgentState['status'];
    dispatch: (action: unknown) => void;
}

interface AgentPanelState {
    showConfig: boolean;
}

class AgentPanel extends Component<AgentPanelProps, AgentPanelState> {
    state: AgentPanelState = {showConfig: false};

    componentDidMount() {
        this.props.dispatch(loadSessions());
        this.props.dispatch(loadStatus());
    }

    handleSelectSession = (sessionId: string) => {
        this.props.dispatch({type: 'agent_set_active_session', payload: sessionId});
        this.props.dispatch(loadMessages(sessionId));
    };

    handleNewChat = () => {
        this.props.dispatch(createSession('新对话', window._projectName || undefined));
    };

    handleDeleteSession = (sessionId: string) => {
        this.props.dispatch(deleteSession(sessionId));
    };

    handleSend = (message: string) => {
        const {activeSessionId} = this.props;
        if (activeSessionId) {
            this.props.dispatch(sendMessage(activeSessionId, message));
        }
    };

    render() {
        const {sessions, activeSessionId, messages, loading, streaming, status} = this.props;
        const {showConfig} = this.state;

        return (
            <div style={{display: 'flex', flexDirection: 'column', height: '100%', background: '#fff'}}>
                {/* Header */}
                <div style={{
                    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                    padding: '8px 12px', borderBottom: '1px solid #e8e8e8'
                }}>
                    <span style={{fontWeight: 600, fontSize: 14}}>
                        <i className="glyphicon glyphicon-education" style={{marginRight: 6}}/>
                        AI 助手
                    </span>
                    <div>
                        <button className="btn btn-xs btn-default" onClick={this.handleNewChat}
                                title="新对话" style={{marginRight: 4}}>
                            <i className="glyphicon glyphicon-plus"/>
                        </button>
                        <button className="btn btn-xs btn-default"
                                onClick={() => this.setState({showConfig: !showConfig})}
                                title="配置" style={{marginRight: 4}}>
                            <i className="glyphicon glyphicon-cog"/>
                        </button>
                    </div>
                </div>

                {/* Config panel (collapsible) */}
                {showConfig && <ConfigPanel dispatch={this.props.dispatch}/>}

                {/* Status bar */}
                {status && (
                    <div style={{
                        padding: '4px 12px', fontSize: 11,
                        background: status.available ? '#f6ffed' : '#fff2f0',
                        color: status.available ? '#52c41a' : '#ff4d4f',
                        borderBottom: '1px solid #e8e8e8'
                    }}>
                        {status.available
                            ? `已连接 · ${status.model}`
                            : '未配置 — 请点击 ⚙ 设置 LLM API'}
                    </div>
                )}

                {/* Main content */}
                {activeSessionId ? (
                    <ChatPanel
                        messages={messages}
                        loading={loading}
                        streaming={streaming}
                        onSend={this.handleSend}
                    />
                ) : (
                    <div style={{flex: 1, overflow: 'auto'}}>
                        {/* Session list */}
                        <div style={{padding: 8}}>
                            {sessions.length === 0 && (
                                <div style={{textAlign: 'center', padding: '40px 20px', color: '#999'}}>
                                    <i className="glyphicon glyphicon-education"
                                       style={{fontSize: 32, display: 'block', marginBottom: 8}}/>
                                    点击 + 开始与 AI 对话
                                </div>
                            )}
                            {sessions.map((s: AgentSession) => (
                                <div key={s.id}
                                     style={{
                                         padding: '8px 12px', cursor: 'pointer',
                                         borderBottom: '1px solid #f0f0f0',
                                         background: s.id === activeSessionId ? '#e6f7ff' : 'transparent'
                                     }}
                                     onClick={() => this.handleSelectSession(s.id)}>
                                    <div style={{fontSize: 13, fontWeight: 500}}>
                                        {s.title || '新对话'}
                                    </div>
                                    <div style={{fontSize: 11, color: '#999', marginTop: 2}}>
                                        {s.updateTime?.substring(0, 16).replace('T', ' ')}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        );
    }
}

const selector = (state: { agent: AgentState }) => ({
    sessions: state.agent?.sessions || [],
    activeSessionId: state.agent?.activeSessionId || null,
    messages: state.agent?.messages || [],
    loading: state.agent?.loading || false,
    streaming: state.agent?.streaming || false,
    status: state.agent?.status || null,
});

const AgentPanelConnected = connect(selector)(AgentPanel);

// Standalone entry
const store = createStore(reducer);

class AgentEntry extends Component {
    render() {
        return (
            <Provider store={store}>
                <AgentPanelConnected/>
            </Provider>
        );
    }
}

const container = document.getElementById('container');
if (container) {
    import('react-dom/client').then(({createRoot}) => {
        createRoot(container).render(<AgentEntry/>);
    });
}

export default AgentPanelConnected;
