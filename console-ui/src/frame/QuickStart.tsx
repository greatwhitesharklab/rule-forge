import {Component} from 'react';
import {message} from 'antd';

interface FeatureItem {
    icon: string;
    title: string;
    desc: string;
    /** 左侧文件树对应分类名 + 右键创建入口名(点击卡片给出指引)。 */
    libName: string;
    createEntry: string;
}

// V7.21 起 BPMN 决策流/决策树/脚本决策集编辑器已删除,资产模型统一为 V1 5 类;
// 卡片标题与文件树分类名(V1决策流/V1规则集/V1决策表/V1评分卡/V1库)对齐
const FEATURES: FeatureItem[] = [
    {icon: 'rf rf-flow',     title: 'V1 决策流',  desc: 'V1 编排:极简节点 + CEL 条件,线性流 + 排他网关', libName: 'V1决策流', createEntry: '添加 V1 决策流'},
    {icon: 'rf rf-rule',     title: 'V1 规则集',  desc: '条件-动作规则集合,供决策流节点引用执行',      libName: 'V1规则集', createEntry: '添加 V1 规则集'},
    {icon: 'rf rf-table',    title: 'V1 决策表',  desc: '表格化条件与结果映射,支持多种命中策略',        libName: 'V1决策表', createEntry: '添加 V1 决策表'},
    {icon: 'rf rf-score',    title: 'V1 评分卡',  desc: '多维度量化打分,聚合输出评估结果',              libName: 'V1评分卡', createEntry: '添加 V1 评分卡'},
    {icon: 'rf rf-variable', title: 'V1 库',      desc: '变量/常量/参数/动作四库,规则引用的公共定义',   libName: 'V1库',     createEntry: '添加 V1 库'},
];

export default class QuickStart extends Component {
    handleCardClick(f: FeatureItem) {
        // 创建入口在文件树分类的右键菜单,QuickStart 拿不到目标节点数据,只做指引
        message.info(`请在左侧文件树的「${f.libName}」分类上右键 → ${f.createEntry}`);
    }

    render() {
        return (
            <div className="welcome-container">
                <div className="welcome-header">
                    <h2 className="welcome-title">欢迎使用 RuleForge 决策平台</h2>
                    <p className="welcome-subtitle">从左侧文件树选择文件开始编辑,或点击下方卡片了解 V1 资产类型</p>
                </div>
                <div className="welcome-grid">
                    {FEATURES.map((f, i) => (
                        <div className="welcome-card welcome-card-clickable" key={i}
                             onClick={() => this.handleCardClick(f)}>
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
