import React, {Component} from 'react';
import * as echarts from 'echarts';

export default class CallVolumeChart extends Component {
    componentDidMount() {
        this.chart = echarts.init(this.container);
        this.updateChart(this.props);
        window.addEventListener('resize', this.handleResize);
    }

    componentDidUpdate(prevProps) {
        if (prevProps.data !== this.props.data) {
            this.updateChart(this.props);
        }
    }

    componentWillUnmount() {
        window.removeEventListener('resize', this.handleResize);
        if (this.chart) this.chart.dispose();
    }

    handleResize = () => {
        if (this.chart) this.chart.resize();
    };

    updateChart(props) {
        const {data} = props;
        if (!data || !data.series) return;

        this.chart.setOption({
            title: {text: '调用量分布', left: 'center', textStyle: {fontSize: 14}},
            tooltip: {trigger: 'axis'},
            grid: {left: 60, right: 20, top: 40, bottom: 30},
            xAxis: {type: 'category', data: data.timestamps || []},
            yAxis: {type: 'value', name: '次'},
            series: [
                {name: '调用量', type: 'bar', data: data.series.count || [], itemStyle: {color: '#5470c6'}}
            ]
        });
    }

    render() {
        return <div ref={el => this.container = el} style={{width: '100%', height: 280}}/>;
    }
}
