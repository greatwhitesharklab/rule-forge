import {Component, ReactNode} from 'react';
import * as echarts from 'echarts';

import {MetricsData} from '../action.js';

interface SuccessFailureChartProps {
    successData?: MetricsData;
    failureData?: MetricsData;
    data?: MetricsData;
}

export default class SuccessFailureChart extends Component<SuccessFailureChartProps> {
    private chart: echarts.ECharts | null = null;
    private container: HTMLDivElement | null = null;

    componentDidMount(): void {
        if (this.container) {
            this.chart = echarts.init(this.container);
        }
        this.updateChart(this.props);
        window.addEventListener('resize', this.handleResize);
    }

    componentDidUpdate(prevProps: SuccessFailureChartProps): void {
        if (prevProps.successData !== this.props.successData || prevProps.failureData !== this.props.failureData) {
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

    updateChart(props: SuccessFailureChartProps): void {
        const {successData, failureData} = props;
        const timestamps = successData?.timestamps || failureData?.timestamps || [];

        if (!this.chart) return;

        this.chart.setOption({
            title: {text: '成功/失败率', left: 'center', textStyle: {fontSize: 14}},
            tooltip: {trigger: 'axis'},
            legend: {data: ['成功', '失败'], bottom: 0},
            grid: {left: 60, right: 20, top: 40, bottom: 40},
            xAxis: {type: 'category', data: timestamps},
            yAxis: {type: 'value', name: '次'},
            series: [
                {
                    name: '成功', type: 'bar', stack: 'total',
                    data: successData?.series?.count || [],
                    itemStyle: {color: '#91cc75'}
                },
                {
                    name: '失败', type: 'bar', stack: 'total',
                    data: failureData?.series?.count || [],
                    itemStyle: {color: '#ee6666'}
                }
            ]
        });
    }

    render(): ReactNode {
        return <div ref={el => { this.container = el; }} style={{width: '100%', height: 320}}/>;
    }
}
