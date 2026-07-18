import { Component, ReactNode } from 'react';
import {Button, Modal} from 'antd';
import * as event from '../../../frame/event.js';

interface DialogButton {
    name: string;
    type?: 'primary' | 'link' | 'default';
    danger?: boolean;
    icon?: ReactNode;
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
        const buttonElements = (buttons || []).map((btn, index) => {
            return (
                <Button key={index} type={btn.type === 'default' ? undefined : btn.type} danger={btn.danger}
                        onClick={() => btn.click(this.props.dispatch)}>
                    {typeof btn.icon === 'string' ? <i className={btn.icon} /> : btn.icon} {btn.name}
                </Button>
            );
        });
        return (
            // 不能加 forceRender:它会让所有 Modal 常驻 @rc-component/portal 的 ESC 栈,
            // Escape 只路由到栈顶(最后挂载的)对话框,可见对话框反而收不到(同 CommonDialog)
            <Modal open={visible} title={title} footer={buttonElements} onCancel={() => this._close()}>
                {body}
            </Modal>
        );
    }
}
