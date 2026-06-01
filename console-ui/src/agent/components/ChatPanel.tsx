import {Component, createRef} from 'react';
import type {AgentMessage} from '../action';

interface ChatPanelProps {
    messages: AgentMessage[];
    loading: boolean;
    streaming: boolean;
    onSend: (message: string) => void;
}

class ChatPanel extends Component<ChatPanelProps> {
    state = {input: ''};
    private listRef = createRef<HTMLDivElement>();

    componentDidUpdate(prevProps: ChatPanelProps) {
        if (prevProps.messages.length !== this.props.messages.length && this.listRef.current) {
            this.listRef.current.scrollTop = this.listRef.current.scrollHeight;
        }
    }

    handleSend = () => {
        const {input} = this.state;
        if (!input.trim() || this.props.loading) return;
        this.props.onSend(input.trim());
        this.setState({input: ''});
    };

    handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            this.handleSend();
        }
    };

    renderMessage(msg: AgentMessage, index: number) {
        const isUser = msg.role === 'user';
        const isStreaming = msg.role === 'streaming';
        const isTool = msg.role === 'tool';

        return (
            <div key={index} style={{
                display: 'flex',
                justifyContent: isUser ? 'flex-end' : 'flex-start',
                padding: '4px 12px',
                marginBottom: 4
            }}>
                <div style={{
                    maxWidth: '85%',
                    padding: '8px 12px',
                    borderRadius: 8,
                    fontSize: 13,
                    lineHeight: 1.6,
                    background: isUser ? '#1677ff' : isTool ? '#f0f0f0' : '#f5f5f5',
                    color: isUser ? '#fff' : '#333',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word'
                }}>
                    {isStreaming && (
                        <span style={{color: '#999', fontSize: 11}}>● ● ●</span>
                    )}
                    {msg.content}
                    {isStreaming && <span className="cursor-blink">▍</span>}
                </div>
            </div>
        );
    }

    render() {
        const {messages, loading} = this.props;

        return (
            <div style={{display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0}}>
                {/* Messages */}
                <div ref={this.listRef} style={{flex: 1, overflow: 'auto', padding: '8px 0'}}>
                    {messages.map((msg, i) => this.renderMessage(msg, i))}
                    {loading && messages[messages.length - 1]?.role !== 'streaming' && (
                        <div style={{textAlign: 'center', padding: 8, color: '#999', fontSize: 12}}>
                            <i className="glyphicon glyphicon-refresh" style={{marginRight: 4}}/>
                            AI 正在思考...
                        </div>
                    )}
                </div>

                {/* Input */}
                <div style={{
                    padding: '8px 12px',
                    borderTop: '1px solid #e8e8e8',
                    display: 'flex',
                    gap: 8
                }}>
                    <textarea
                        value={this.state.input}
                        onChange={(e) => this.setState({input: e.target.value})}
                        onKeyDown={this.handleKeyDown}
                        placeholder="输入消息，Enter 发送..."
                        disabled={loading}
                        style={{
                            flex: 1, resize: 'none', border: '1px solid #d9d9d9',
                            borderRadius: 6, padding: '6px 10px', fontSize: 13,
                            minHeight: 36, maxHeight: 120, outline: 'none'
                        }}
                        rows={1}
                    />
                    <button
                        onClick={this.handleSend}
                        disabled={loading || !this.state.input.trim()}
                        style={{
                            background: '#1677ff', color: '#fff', border: 'none',
                            borderRadius: 6, padding: '0 16px', cursor: loading ? 'not-allowed' : 'pointer',
                            fontSize: 13, opacity: loading ? 0.5 : 1
                        }}>
                        发送
                    </button>
                </div>
            </div>
        );
    }
}

export default ChatPanel;
