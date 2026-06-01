import React, {Component} from 'react';

interface ClickableLabelProps {
    text: string;
    color?: string;
    onClick: (e: React.MouseEvent<HTMLSpanElement>) => void;
}

interface ClickableLabelState {
    hovered: boolean;
}

const styles: Record<string, React.CSSProperties> = {
    label: {
        height: 20,
        cursor: 'pointer',
        margin: 0,
        color: 'white',
        border: '1px dashed transparent',
    },
    hover: {
        border: '1px dashed gray',
    },
};

export default class ClickableLabel extends Component<ClickableLabelProps, ClickableLabelState> {
    state = {hovered: false};

    handleMouseEnter = () => this.setState({hovered: true});
    handleMouseLeave = () => this.setState({hovered: false});

    render() {
        const {text, color, onClick} = this.props;
        const {hovered} = this.state;
        return (
            <span
                style={{
                    ...styles.label,
                    ...(hovered ? styles.hover : {}),
                    color: color || 'white',
                }}
                onMouseEnter={this.handleMouseEnter}
                onMouseLeave={this.handleMouseLeave}
                onClick={onClick}
            >
                {text}
            </span>
        );
    }
}
