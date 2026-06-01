import '../css/loading.css';
import * as event from '../../componentEvent.js';
import { Component } from 'react';

interface LoadingProps {
    show?: boolean;
}

interface LoadingState {
    visible: boolean;
    content: string;
}

export default class Loading extends Component<LoadingProps, LoadingState> {
    private _onShow: ((content?: string) => void) | null = null;
    private _onHide: (() => void) | null = null;

    constructor(props: LoadingProps) {
        super(props);
        this.state = { visible: !!props.show, content: '数据加载中...' };
    }

    componentDidMount() {
        this._onShow = (content?: string) => {
            if (content) {
                this.setState({ visible: true, content });
            } else {
                this.setState({ visible: true, content: '数据加载中...' });
            }
        };
        this._onHide = () => {
            this.setState({ visible: false });
        };
        event.eventEmitter.on(event.SHOW_LOADING, this._onShow);
        event.eventEmitter.on(event.HIDE_LOADING, this._onHide);
    }

    componentWillUnmount() {
        if (this._onShow) {
            event.eventEmitter.removeListener(event.SHOW_LOADING, this._onShow);
        }
        if (this._onHide) {
            event.eventEmitter.removeListener(event.HIDE_LOADING, this._onHide);
        }
    }

    render() {
        if (!this.state.visible) {
            return null;
        }
        const width = window.innerWidth;
        const height = window.innerHeight;
        const styleObj: React.CSSProperties = { width, height, display: 'block', transition: 'opacity 0.2s' };
        const coverTop = (height / 2) - 20;
        const coverLeft = (width / 2) - 20;
        const loadStyle: React.CSSProperties = { marginTop: coverTop, marginLeft: coverLeft, width: '40px', height: '40px' };
        return (
            <div className="loading-cover" style={styleObj}>
                <div className="spinner" style={loadStyle}>
                    <div className="spinner-container container1">
                        <div className="circle1"></div>
                        <div className="circle2"></div>
                        <div className="circle3"></div>
                        <div className="circle4"></div>
                    </div>
                    <div className="spinner-container container2">
                        <div className="circle1"></div>
                        <div className="circle2"></div>
                        <div className="circle3"></div>
                        <div className="circle4"></div>
                    </div>
                    <div className="spinner-container container3">
                        <div className="circle1"></div>
                        <div className="circle2"></div>
                        <div className="circle3"></div>
                        <div className="circle4"></div>
                    </div>
                </div>
            </div>
        );
    }
}
