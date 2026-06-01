import {Component, ReactNode} from 'react';
import * as echarts from 'echarts';

import {MetricsData} from '../action.js';

interface CallVolumeChartProps {
    data: MetricsData;
}

export default class CallVolumeChart extends Component<CallVolumeChartProps> {
    private chart: echarts.ECharts | null = null;
    private container: HTMLDivElement | null = null;

    componentDidMount(): void {
        if (this.container) {
            this.chart = echarts.init(this.container);
        }
        this.updateChart(this.props);
        window.addEventListener('resize', this.handleResize);
    }

    componentDidUpdate(prevProps: CallVolumeChartProps): void {
        if (prevProps.data !== this.props.data) {
            this.updateChart(this.props);
        }
    }

    componentWillUnmount(): void {
        window.removeEventListener('resize', this.handleResize);
        if (this.chart) this.chart.dispose();
    }

    handleResize = (): void => {
        if (this.chart) this.chart.resize();
    };

    updateChart(props: CallVolumeChartProps): void {
        const {data} = props;
        if (!this.chart || !data || !data.series) return;

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

    render(): ReactNode {
        return <div ref={el => { this.container = el; }} style={{width: '100%', height: 280}}/>;
    }
}
