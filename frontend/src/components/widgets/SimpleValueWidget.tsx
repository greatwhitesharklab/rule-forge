import React, {Component, createRef} from 'react';

const TIP = '请输入值';

interface SimpleValueWidgetProps {
    initialData?: { content?: string };
    onDirty?: () => void;
    ref?: React.Ref<any>;
}

interface SimpleValueWidgetState {
    text: string;
    editing: boolean;
    inputWidth: number;
}

export default class SimpleValueWidget extends Component<SimpleValueWidgetProps, SimpleValueWidgetState> {
    state = {
        text: '',
        editing: false,
        inputWidth: 120,
    };
    inputRef = createRef<HTMLInputElement>();
    containerRef: HTMLSpanElement | null = null;

    componentDidMount() {
        if (this.props.initialData) {
            this.initData(this.props.initialData);
        }
    }

    initData(data: { content?: string }) {
        if (!data) return;
        this.setState({text: data.content || ''});
    }

    getValue(): string {
        let v = this.state.text;
        v = v.replace(/&/g, '&amp;');
        v = v.replace(/</g, '&lt;');
        v = v.replace(/>/g, '&gt;');
        v = v.replace(/'/g, '&apos;');
        v = v.replace(/"/g, '&quot;');
        return v;
    }

    getDisplayText(): string {
        return this.state.text;
    }

    handleLabelClick = () => {
        let maxWidth = 120;
        const el = this.containerRef?.parentElement?.parentElement?.parentElement;
        if (el && el.className === 'htMiddle htDimmed current') {
            maxWidth = el.offsetWidth - 20;
        }
        this.setState({editing: true, inputWidth: maxWidth}, () => {
            this.inputRef.current?.focus();
        });
    };

    handleBlur = () => {
        this.setState({editing: false});
        if (this.props.onDirty) this.props.onDirty();
    };

    render() {
        const {text, editing, inputWidth} = this.state;
        const display = text || TIP;
        const color = text ? 'rgb(180,95,4)' : undefined;

        return (
            <span ref={el => { this.containerRef = el; }}>
                {!editing ? (
                    <span
                        style={{color, cursor: 'pointer', height: 20}}
                        onClick={this.handleLabelClick}
                    >
                        {display}
                    </span>
                ) : (
                    <input
                        ref={this.inputRef}
                        type="text"
                        className="form-control rule-text-editor"
                        style={{height: 22, width: inputWidth}}
                        value={text}
                        onChange={e => this.setState({text: e.target.value})}
                        onBlur={this.handleBlur}
                        onMouseDown={e => e.stopPropagation()}
                        onKeyDown={e => e.stopPropagation()}
                    />
                )}
            </span>
        );
    }
}
