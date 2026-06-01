import {Component, ReactNode} from 'react';
import * as echarts from 'echarts';

import {MetricsData} from '../action.js';

interface LatencyChartProps {
    data: MetricsData;
}

export default class LatencyChart extends Component<LatencyChartProps> {
    private chart: echarts.ECharts | null = null;
    private container: HTMLDivElement | null = null;

    componentDidMount(): void {
        if (this.container) {
            this.chart = echarts.init(this.container);
        }
        this.updateChart(this.props);
        window.addEventListener('resize', this.handleResize);
    }

    componentDidUpdate(prevProps: LatencyChartProps): void {
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

    updateChart(props: LatencyChartProps): void {
        const {data} = props;
        if (!this.chart || !data || !data.series) return;

        this.chart.setOption({
            title: {text: '执行耗时趋势', left: 'center', textStyle: {fontSize: 14}},
            tooltip: {trigger: 'axis'},
            legend: {data: ['P50', 'P95', 'P99'], bottom: 0},
            grid: {left: 60, right: 20, top: 40, bottom: 40},
            xAxis: {type: 'category', data: data.timestamps || []},
            yAxis: {type: 'value', name: 'ms', axisLabel: {formatter: '{value}'}},
            series: [
                {name: 'P50', type: 'line', data: data.series.p50 || [], smooth: true, itemStyle: {color: '#5470c6'}},
                {name: 'P95', type: 'line', data: data.series.p95 || [], smooth: true, itemStyle: {color: '#ee6666'}},
                {name: 'P99', type: 'line', data: data.series.p99 || [], smooth: true, itemStyle: {color: '#fac858'}}
            ]
        });
    }

    render(): ReactNode {
        return <div ref={el => { this.container = el; }} style={{width: '100%', height: 320}}/>;
    }
}
