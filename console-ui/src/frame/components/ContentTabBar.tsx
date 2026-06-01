import {Component, RefObject} from 'react';

interface TabData {
    fullPath: string;
    label: string;
}

interface FrameTabRef {
    activateTab(fullPath: string): void;
    closeTab(fullPath: string): void;
}

interface ContentTabBarProps {
    getFrameTabRef?: () => FrameTabRef | null;
}

interface ContentTabBarState {
    tabs: TabData[];
    activeTab: string | null;
}

export default class ContentTabBar extends Component<ContentTabBarProps, ContentTabBarState> {
    constructor(props: ContentTabBarProps) {
        super(props);
        this.state = {
            tabs: [],
            activeTab: null
        };
    }

    setTabData(tabs: TabData[], activeTab: string | null) {
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
