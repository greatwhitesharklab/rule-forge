// echarts 按需引入入口 — 替代 `import * as echarts from 'echarts'` 全量包(省 ~1MB)。
// 三个监控图表(CallVolumeChart/SuccessFailureChart/LatencyChart)只用到
// bar/line 系列 + title/tooltip/legend/grid 组件 + Canvas 渲染,新增能力时在此注册。
import * as echarts from 'echarts/core';
import {BarChart, LineChart} from 'echarts/charts';
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components';
import {CanvasRenderer} from 'echarts/renderers';

echarts.use([
    BarChart,
    LineChart,
    TitleComponent,
    TooltipComponent,
    LegendComponent,
    GridComponent,
    CanvasRenderer,
]);

export default echarts;
