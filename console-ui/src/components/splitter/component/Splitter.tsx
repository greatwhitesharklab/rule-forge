import { Component, createRef, ReactNode } from 'react';

type Position = number | string;

interface SplitterProps {
    position?: Position;
    orientation?: 'vertical' | 'horizontal';
    limit?: number | string;
    children?: ReactNode;
}

interface SplitterState {
    position: Position;
    hovering: boolean;
}

export default class Splitter extends Component<SplitterProps, SplitterState> {
    private containerRef = createRef<HTMLDivElement>();
    private _dragging = false;
    private _startPos: Position = 0;
    private _startClient = 0;
    private _onMouseDown: (e: MouseEvent) => void;
    private _onMouseMove: (e: MouseEvent) => void;
    private _onMouseUp: () => void;
    private _onResize: () => void;
    private _onMouseEnter: () => void;
    private _onMouseLeave: () => void;

    constructor(props: SplitterProps) {
        super(props);
        const position = this._parsePosition(props.position || '50%');
        this.state = { position, hovering: false };

        this._onMouseDown = this.__onMouseDown.bind(this);
        this._onMouseMove = this.__onMouseMove.bind(this);
        this._onMouseUp = this.__onMouseUp.bind(this);
        this._onResize = this.__onResize.bind(this);
        this._onMouseEnter = () => this.setState({ hovering: true });
        this._onMouseLeave = () => this.setState({ hovering: false });
    }

    _parsePosition(position: Position): Position {
        if (typeof position === 'number') {
            return position;
        } else if (typeof position === 'string') {
            const match = position.match(/^([0-9\.]+)(px|%)$/);
            if (match) {
                if (match[2] === 'px') {
                    return parseFloat(match[1]);
                } else {
                    return position;
                }
            }
        }
        return position;
    }

    _resolvePosition(position: Position, size: number): number {
        if (typeof position === 'number') {
            return position;
        } else if (typeof position === 'string') {
            let match = position.match(/^([0-9\.]+)%$/);
            if (match) {
                return (size * parseFloat(match[1])) / 100;
            }
            match = position.match(/^([0-9\.]+)px$/);
            if (match) {
                return parseFloat(match[1]);
            }
        }
        return typeof position === 'number' ? position : 0;
    }

    _getLimit(): number {
        return parseInt(this.props.limit as string, 10) || 100;
    }

    componentDidMount() {
        const domNode = this.containerRef.current;
        if (!domNode) return;
        domNode.style.height = '100%';

        const size = this._isVertical() ? domNode.offsetWidth : domNode.offsetHeight;
        let resolved = this._resolvePosition(this.state.position, size);
        const limit = this._getLimit();
        resolved = Math.max(resolved, limit);
        resolved = Math.min(resolved, size - limit);
        this.setState({ position: resolved });

        window.addEventListener('resize', this._onResize);
    }

    componentWillUnmount() {
        window.removeEventListener('resize', this._onResize);
        document.removeEventListener('mousemove', this._onMouseMove);
        document.removeEventListener('mouseup', this._onMouseUp);
    }

    _isVertical(): boolean {
        return this.props.orientation === 'vertical';
    }

    __onResize() {
        const domNode = this.containerRef.current;
        if (!domNode) return;

        const size = this._isVertical() ? domNode.offsetWidth : domNode.offsetHeight;
        const limit = this._getLimit();
        let pos: Position = this.state.position;
        if (typeof pos === 'string') {
            pos = this._resolvePosition(pos, size);
        }
        pos = Math.max(pos, limit);
        pos = Math.min(pos, size - limit);
        this.setState({ position: pos });
    }

    __onMouseDown(e: MouseEvent) {
        e.preventDefault();
        this._dragging = true;
        this._startPos = this.state.position;
        this._startClient = this._isVertical() ? e.clientX : e.clientY;
        document.addEventListener('mousemove', this._onMouseMove);
        document.addEventListener('mouseup', this._onMouseUp);
        document.body.style.cursor = this._isVertical() ? 'col-resize' : 'row-resize';
        document.body.style.userSelect = 'none';
    }

    __onMouseMove(e: MouseEvent) {
        if (!this._dragging) return;
        const domNode = this.containerRef.current;
        if (!domNode) return;

        const limit = this._getLimit();
        const size = this._isVertical() ? domNode.offsetWidth : domNode.offsetHeight;
        const client = this._isVertical() ? e.clientX : e.clientY;
        const rect = domNode.getBoundingClientRect();
        const offset = this._isVertical() ? rect.left : rect.top;
        let pos = client - offset;

        pos = Math.max(pos, limit);
        pos = Math.min(pos, size - limit);
        this.setState({ position: pos });
    }

    __onMouseUp() {
        this._dragging = false;
        document.removeEventListener('mousemove', this._onMouseMove);
        document.removeEventListener('mouseup', this._onMouseUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
    }

    render() {
        const children = this.props.children ? (Array.isArray(this.props.children) ? this.props.children : [this.props.children]) : [];
        const first = children[0] as ReactNode;
        const second = children[1] as ReactNode;
        const isVertical = this._isVertical();
        const position = this.state.position;

        let firstStyle: React.CSSProperties, secondStyle: React.CSSProperties, dividerStyle: React.CSSProperties;
        if (isVertical) {
            firstStyle = {
                position: 'absolute',
                top: 0,
                left: 0,
                height: '100%',
                width: typeof position === 'number' ? position : position,
                overflow: 'auto'
            };
            secondStyle = {
                position: 'absolute',
                top: 0,
                right: 0,
                height: '100%',
                width: typeof position === 'number' ? ('calc(100% - ' + position + 'px)') : '50%',
                overflow: 'auto'
            };
            dividerStyle = {
                position: 'absolute',
                top: 0,
                height: '100%',
                width: (this.state.hovering || this._dragging) ? '3px' : '1px',
                left: typeof position === 'number' ? position + 'px' : '50%',
                background: (this.state.hovering || this._dragging) ? 'var(--rf-primary)' : 'var(--rf-border-split)',
                cursor: 'col-resize',
                zIndex: 900,
                transition: this._dragging ? 'none' : 'all 0.15s ease'
            };
        } else {
            firstStyle = {
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: typeof position === 'number' ? position : position,
                overflow: 'auto'
            };
            secondStyle = {
                position: 'absolute',
                left: 0,
                bottom: 0,
                width: '100%',
                height: typeof position === 'number' ? ('calc(100% - ' + position + 'px)') : '50%',
                overflow: 'auto'
            };
            dividerStyle = {
                position: 'absolute',
                left: 0,
                width: '100%',
                height: (this.state.hovering || this._dragging) ? '3px' : '1px',
                top: typeof position === 'number' ? position + 'px' : '50%',
                background: (this.state.hovering || this._dragging) ? 'var(--rf-primary)' : 'var(--rf-border-split)',
                cursor: 'row-resize',
                zIndex: 800,
                transition: this._dragging ? 'none' : 'all 0.15s ease'
            };
        }

        return (
            <div ref={this.containerRef} style={{ position: 'relative', flex: 1, minWidth: 0, minHeight: 0 }}>
                <div style={firstStyle}>{first}</div>
                <div style={dividerStyle} onMouseDown={this._onMouseDown as unknown as React.MouseEventHandler}
                    onMouseEnter={this._onMouseEnter} onMouseLeave={this._onMouseLeave}></div>
                <div style={secondStyle}>{second}</div>
            </div>
        );
    }
}
