import {Component} from 'react';

interface FeatureItem {
    icon: string;
    title: string;
    desc: string;
}

const FEATURES: FeatureItem[] = [
    {icon: 'rf rf-rule',    title: '向导式决策集',  desc: '可视化规则配置，支持多条件组合'},
    {icon: 'rf rf-script',  title: '脚本式决策集',  desc: 'UL脚本编写，灵活定义规则逻辑'},
    {icon: 'rf rf-table',   title: '决策表',        desc: '表格化决策，清晰的条件与结果映射'},
    {icon: 'rf rf-tree',    title: '决策树',        desc: '树形决策路径，直观的分支判断'},
    {icon: 'rf rf-score',   title: '评分卡',        desc: '量化评估模型，多维度打分'},
    {icon: 'rf rf-flow',    title: '决策流',        desc: 'BPMN流程编排，复杂决策链路'},
];

export default class QuickStart extends Component {
    render() {
        return (
            <div className="welcome-container">
                <div className="welcome-header">
                    <h2 className="welcome-title">欢迎使用 RuleForge 决策平台</h2>
                    <p className="welcome-subtitle">从左侧文件树选择文件开始编辑，或浏览下方功能模块</p>
                </div>
                <div className="welcome-grid">
                    {FEATURES.map((f, i) => (
                        <div className="welcome-card" key={i}>
                            <div className="welcome-card-icon"><i className={f.icon}/></div>
                            <div className="welcome-card-body">
                                <div className="welcome-card-title">{f.title}</div>
                                <div className="welcome-card-desc">{f.desc}</div>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        );
    }
}
