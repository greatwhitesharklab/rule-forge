import { Component, ReactNode, RefObject } from 'react';

interface DialogButton {
    name: string;
    className: string;
    icon?: string;
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
    forwardedRef?: RefObject<HTMLDivElement | null>;
}

export default class CommonDialog extends Component<CommonDialogProps> {
    _close(): void {
        if (this.props.onClose) {
            this.props.onClose();
        }
    }

    render() {
        const { visible, title, body, buttons, large, dialogStyle, dispatch, info, htmlContent } = this.props;
        const largeClass = large ? ' modal-lg' : '';
        const dialogStyleObj = dialogStyle || {};
        const buttonElements = (buttons || []).map((btn, index) => (
            <button type="button" key={index} className={btn.className} onClick={() => btn.click(dispatch)}>
                <i className={btn.icon} /> {btn.name}
            </button>
        ));
        return (
            <div ref={this.props.forwardedRef}>
                {visible && <div className="modal-backdrop fade in" onClick={() => this._close()}></div>}
                <div className={`modal fade ${visible ? 'in' : ''}`}
                     style={{ display: visible ? 'block' : 'none', overflow: 'auto' }}
                     tabIndex={-1} role="dialog" aria-hidden={!visible}>
                    <div className={`modal-dialog${largeClass}`} style={dialogStyleObj}>
                        <div className="modal-content">
                            <div className="modal-header">
                                <button type="button" className="close" aria-hidden="true" onClick={() => this._close()}>&times;</button>
                                <h3 className="modal-title" style={{ wordWrap: 'break-word', display: 'flex', alignItems: 'center', fontWeight: 'var(--rf-font-weight-semibold)' }}>
                                    {title}
                                    {info && <div className="text-danger" style={{ fontSize: '12pt', color: 'var(--rf-error)' }}>{info}</div>}
                                    {htmlContent && <div style={{ display: 'inline-block', marginLeft: 'var(--rf-space-3)' }}>{htmlContent}</div>}
                                </h3>
                            </div>
                            <div className="modal-body" style={{ padding: 'var(--rf-space-6)' }}>
                                {body}
                            </div>
                            <div className="modal-footer">
                                {buttonElements}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}
