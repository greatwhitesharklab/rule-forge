import React,{Component} from 'react';

export default class IFrame extends Component{
    constructor(props) {
        super(props);
        this.iframeRef = React.createRef();
        this._onResize = this._onResize.bind(this);
    }

    componentDidMount(){
        const iframe = this.iframeRef.current;
        this._updateHeight();
        iframe.addEventListener('load', () => {
            this._injectHeightStyles(iframe);
            this._updateHeight();
        });
        window.addEventListener('resize', this._onResize);
    }

    componentWillUnmount() {
        window.removeEventListener('resize', this._onResize);
    }

    _onResize() {
        this._updateHeight();
    }

    _injectHeightStyles(iframe) {
        try {
            const doc = iframe.contentDocument;
            if (!doc || doc.getElementById('rf-height-fix')) return;
            const style = doc.createElement('style');
            style.id = 'rf-height-fix';
            style.textContent = 'html,body,#container{height:100%;margin:0}';
            doc.head.appendChild(style);
        } catch(e) {}
    }

    _updateHeight() {
        const iframe = this.iframeRef.current;
        if (!iframe) return;
        const parent = iframe.parentElement;
        const h = parent ? parent.offsetHeight : 0;
        if (h > 0) {
            iframe.style.height = h + 'px';
        } else {
            iframe.style.height = document.documentElement.clientHeight + 'px';
        }
    }

    render(){
        const path=encodeURI(encodeURI(this.props.path));
        const iframeId=this.props.id;
        return (
            <iframe ref={this.iframeRef} src={path} id={iframeId}
                    style={{width:'100%', border:0}}
                    frameBorder="none"></iframe>
        );
    }
}
