import React, {Component} from 'react';

export default class ContentTabBar extends Component {
    constructor(props) {
        super(props);
        this.state = {
            tabs: [],
            activeTab: null
        };
    }

    setTabData(tabs, activeTab) {
        this.setState({tabs, activeTab});
    }

    render() {
        const {tabs, activeTab} = this.state;
        const getFrameTabRef = this.props.getFrameTabRef;

        if (tabs.length === 0) return null;

        return (
            <div className="content-tab-bar">
                {tabs.map(tab => (
                    <div key={tab.fullPath}
                         className={'content-tab' + (tab.fullPath === activeTab ? ' active' : '')}>
                        <span className="content-tab-label" onClick={() => {
                            const ref = getFrameTabRef && getFrameTabRef();
                            if (ref) ref.activateTab(tab.fullPath);
                        }}>{tab.label}</span>
                        <button className="content-tab-close" onClick={(e) => {
                            e.stopPropagation();
                            const ref = getFrameTabRef && getFrameTabRef();
                            if (ref) ref.closeTab(tab.fullPath);
                        }}>×</button>
                    </div>
                ))}
            </div>
        );
    }
}
