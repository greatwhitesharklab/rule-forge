import {Component} from 'react';
import {Badge, Popover, Empty, Button, Tag} from 'antd';
import {BellOutlined} from '@ant-design/icons';
import {jsonPost} from '@/api/client';
import * as componentEvent from '@/components/componentEvent.js';

// ====== 类型定义 ======

/**
 * V5.45.5 — 复用 DraftsView 里的 DraftDto 形状。本组件**不**直接 import DraftsView
 * (避免 antd Popover + DraftsView 列表组件双层嵌入的循环依赖),只读需要的字段。
 */
interface AlertDraft {
    draftId: string;
    ruleType: string;
    project: string;
    status: 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EXPIRED';
    title: string | null;
    source: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
    reviewedBy: string | null;
    reviewedAt: string | null;
    reviewComment: string | null;
}

interface AlertBellProps {
    username?: string;
    /** V5.45.5 — polling 间隔 ms,默认 30000(plan R9:V5.45.5 走 polling,SSE 接 V5.46+) */
    pollIntervalMs?: number;
}

interface AlertBellState {
    drafts: AlertDraft[];
    loading: boolean;
    open: boolean;
}

/**
 * V5.45.5 — V5.22 AI 收口 Frame alert bell。
 *
 * <p>frame 顶部铃铛 — 展示"待我审"的 draft(PENDING_REVIEW 状态)。点击展开
 * popover,列最近 5 条,带"去审批"按钮跳 DraftsView。
 *
 * <p>数据源:`/agent/tools/list_drafts` V5.22.3 已存在(plan 不需要新端点)。
 * 30s polling — 后端单查询 < 50ms,可接受。
 *
 * <p>SSE 实时订阅留 V5.46+ — 跟 plan R9 一致。
 */
export default class AlertBell extends Component<AlertBellProps, AlertBellState> {
    private pollTimer: number | null = null;
    /** B-1:连续失败计数,达到 MAX_FAILS 停止轮询(后端端点不可用时不刷屏 404) */
    private failCount = 0;
    private static readonly MAX_FAILS = 3;

    state: AlertBellState = {
        drafts: [],
        loading: false,
        open: false,
    };

    componentDidMount() {
        this.load();
        const interval = this.props.pollIntervalMs ?? 30000;
        this.pollTimer = window.setInterval(() => this.load(), interval);
    }

    componentWillUnmount() {
        if (this.pollTimer !== null) {
            window.clearInterval(this.pollTimer);
            this.pollTimer = null;
        }
    }

    load = async () => {
        // V5.45.5 — 只拉 PENDING_REVIEW,后端 list_drafts 接受 status 过滤
        // (DraftsView.tsx:136 用过)
        this.setState({loading: true});
        try {
            const data = await jsonPost<{drafts: AlertDraft[]; count: number}>(
                '/agent/tools/list_drafts',
                {status: 'PENDING_REVIEW', limit: 5},
                {silent: true}
            );
            this.failCount = 0;
            this.setState({drafts: data.drafts || [], loading: false});
        } catch {
            // 静默失败 — 不打扰用户。
            // B-1 修复:连续失败 MAX_FAILS 次后停止轮询(后端端点不可用时不再刷屏 404)。
            this.failCount++;
            this.setState({loading: false});
            if (this.failCount >= AlertBell.MAX_FAILS && this.pollTimer !== null) {
                window.clearInterval(this.pollTimer);
                this.pollTimer = null;
            }
        }
    };

    // UX-B3 修复:open 只由 Popover trigger="click" 驱动。原实现按钮自身 onClick 也
    // 切换 open,而 @rc-component/trigger 先以 flushSync 调 onOpenChange(true)、再执行
    // 按钮的 onClick —— 后者读到已更新的 state 又切回 false,弹层"开了又立刻关",
    // 表现为点击铃铛无响应。
    handleOpenChange = (open: boolean) => {
        this.setState({open});
        // 展开时强制 reload(用户可能停留了 30s+ 没看)
        if (open) this.load();
    };

    handleReviewClick = (draft: AlertDraft) => {
        // V5.45.5 — 跳 AgentPanel + DraftsView。V5.22 AgentPanel 已经有 DraftsView
        // 入口,通过 TREE_NODE_CLICK 触发 navigation 不太合适,直接用 componentEvent
        // emit 一个自定义事件,AgentPanel 监听后切到 drafts tab
        componentEvent.eventEmitter.emit('OPEN_DRAFT_REVIEW' as any, draft);
        this.setState({open: false});
    };

    renderPopoverContent() {
        const {drafts, loading} = this.state;
        if (loading && drafts.length === 0) {
            return <div style={{padding: 16, color: '#999'}}>加载中…</div>;
        }
        if (drafts.length === 0) {
            return <Empty description="无待审 draft" image={Empty.PRESENTED_IMAGE_SIMPLE}/>;
        }
        return (
            <div style={{width: 320, maxHeight: 400, overflowY: 'auto'}}>
                {drafts.map((d) => (
                    <div
                        key={d.draftId}
                        style={{
                            padding: '10px 8px',
                            borderBottom: '1px solid #f0f0f0',
                            display: 'flex',
                            alignItems: 'center',
                            gap: 8,
                        }}
                    >
                        <Tag color="orange">待审</Tag>
                        <div style={{flex: 1, minWidth: 0}}>
                            <div
                                style={{
                                    fontSize: 13,
                                    fontWeight: 500,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                }}
                                title={d.title || d.draftId}
                            >
                                {d.title || d.draftId}
                            </div>
                            <div style={{fontSize: 11, color: '#999', marginTop: 2}}>
                                {d.project} · {d.ruleType} · {d.createdBy}
                            </div>
                        </div>
                        <Button
                            size="small"
                            type="primary"
                            onClick={() => this.handleReviewClick(d)}
                        >
                            去审批
                        </Button>
                    </div>
                ))}
            </div>
        );
    }

    render() {
        const {drafts} = this.state;
        const {open} = this.state;
        const count = drafts.length;

        return (
            <Popover
                content={this.renderPopoverContent()}
                title="待我审的 draft"
                trigger="click"
                open={open}
                onOpenChange={this.handleOpenChange}
                placement="bottomRight"
            >
                <button
                    data-testid="alert-bell-btn"
                    className="topbar-bell"
                    aria-label="待审通知"
                >
                    <Badge count={count} size="small" offset={[-2, 2]} overflowCount={99}>
                        <BellOutlined/>
                    </Badge>
                </button>
            </Popover>
        );
    }
}
