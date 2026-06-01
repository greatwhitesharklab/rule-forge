import { Component, ReactNode } from 'react';
import * as event from '../../../frame/event.js';

interface DialogButton {
    name: string;
    className: string;
    icon: string;
    click: (dispatch?: unknown) => void;
}

interface DialogProps {
    title?: ReactNode;
    buttons?: DialogButton[];
    body?: ReactNode;
    visible?: boolean;
    onClose?: () => void;
    dispatch?: unknown;
}

interface DialogState {
    title: ReactNode;
    buttons: DialogButton[];
    body: ReactNode;
    visible: boolean;
    init: ((dispatch?: unknown) => void) | null;
    destroy: (() => void) | null;
}

export default class Dialog extends Component<DialogProps, DialogState> {
    constructor(props: DialogProps) {
        super(props);
        this.state = {
            title: this.props.title || '',
            buttons: this.props.buttons || [],
            body: this.props.body || [],
            visible: this.props.visible || false,
            init: null,
            destroy: null
        };
    }

    componentDidMount(): void {
        event.eventEmitter.on(event.OPEN_DIALOG, (data: Partial<DialogState>) => {
            this.setState({
                title: data.title || this.state.title,
                body: data.body || this.state.body,
                buttons: data.buttons || this.state.buttons,
                init: data.init || null,
                destroy: data.destroy || null,
                visible: true
            });
        });
        event.eventEmitter.on(event.CLOSE_DIALOG, () => {
            this.setState({ visible: false });
        });
        event.eventEmitter.on(event.DIALOG_CONTNET_CHANGE, (data: Partial<DialogState>) => {
            this.setState({
                title: data.title || this.state.title,
                body: data.body || this.state.body,
                buttons: data.buttons || this.state.buttons
            });
        });
    }

    componentWillUnmount(): void {
        event.eventEmitter.removeAllListeners(event.OPEN_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_DIALOG);
    }

    componentDidUpdate(prevProps: DialogProps, prevState: DialogState): void {
        if (this.props.visible !== undefined && this.props.visible !== prevProps.visible) {
            this.setState({ visible: this.props.visible! });
        }
        if (this.props.title !== undefined && this.props.title !== prevProps.title) {
            this.setState({ title: this.props.title! });
        }
        if (this.props.body !== prevProps.body) {
            this.setState({ body: this.props.body });
        }
        if (this.props.buttons !== prevProps.buttons) {
            this.setState({ buttons: this.props.buttons || [] });
        }
        if (!prevState.visible && this.state.visible && this.state.init) {
            this.state.init(this.props.dispatch);
        }
    }

    _close(): void {
        if (this.props.onClose) {
            this.props.onClose();
        } else {
            this.setState({ visible: false });
        }
    }

    render() {
        const { visible, title, body, buttons } = this.state;
        const buttonElements = (buttons || []).map((btn, index) => (
            <button type="button" key={index} className={btn.className} onClick={() => btn.click(this.props.dispatch)}>
                <i className={btn.icon} /> {btn.name}
            </button>
        ));
        return (
            <div>
                {visible && <div className="modal-backdrop fade in" onClick={() => this._close()}></div>}
                <div className={`modal fade ${visible ? 'in' : ''}`}
                     style={{ display: visible ? 'block' : 'none' }}
                     tabIndex={-1} role="dialog" aria-hidden={!visible}>
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <div className="modal-header" style={{ borderBottom: '1px solid var(--rf-border-split)' }}>
                                <button type="button" className="close" aria-hidden="true" onClick={() => this._close()}>&times;</button>
                                <h4 className="modal-title" style={{ fontWeight: 'var(--rf-font-weight-semibold)', color: 'var(--rf-text-primary)' }}>{title}</h4>
                            </div>
                            <div className="modal-body" style={{ padding: 'var(--rf-space-6)', color: 'var(--rf-text-primary)' }}>{body}</div>
                            <div className="modal-footer">{buttonElements}</div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}
