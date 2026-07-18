import {Component, ReactNode} from 'react';
import {Button, Modal} from 'antd';

interface DialogButton {
    name: string;
    type?: 'primary' | 'link' | 'default';
    danger?: boolean;
    icon?: ReactNode;
    click: (dispatch?: unknown) => void;
}

interface CommonDialogProps {
    visible: boolean;
    title: ReactNode;
    body?: ReactNode;
    buttons?: DialogButton[];
    large?: boolean;
    dialogStyle?: React.CSSProperties;
    dispatch?: unknown;
    info?: string;
    htmlContent?: ReactNode;
    onClose?: () => void;
}

/**
 * V5.101.2:rf-modal 手写 modal → antd Modal。props API 不变(visible/title/body/buttons/
 * large/dialogStyle/dispatch/info/htmlContent/onClose),22 个消费方零改动。footer 的 buttons
 * 使用显式 type/danger props(替代原 className 字符串契约)→ antd Button type/danger。
 */
export default class CommonDialog extends Component<CommonDialogProps> {
    render() {
        const {visible, title, body, buttons, large, dialogStyle, dispatch, info, htmlContent, onClose} = this.props;
        const buttonElements = (buttons || []).map((btn, index) => {
            return (
                <Button key={index} type={btn.type === 'default' ? undefined : btn.type} danger={btn.danger}
                        onClick={() => btn.click(dispatch)}>
                    {btn.icon && (typeof btn.icon === 'string' ? <i className={btn.icon}/> : btn.icon)} {btn.name}
                </Button>
            );
        });
        const titleNode = (info || htmlContent) ? (
            <>
                {title}
                {info && <div style={{fontSize: '12pt', color: 'var(--rf-danger)'}}>{info}</div>}
                {htmlContent && <div style={{display: 'inline-block', marginLeft: 'var(--rf-space-3)'}}>{htmlContent}</div>}
            </>
        ) : title;
        return (
            <Modal open={visible} title={titleNode} footer={buttonElements} onCancel={onClose}
                   width={large ? 960 : 520} style={dialogStyle} forceRender>
                {body}
            </Modal>
        );
    }
}
