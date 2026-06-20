package com.ruleforge.runtime;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.engine.Context;
import com.ruleforge.runtime.agenda.Agenda;
import com.ruleforge.runtime.rete.ContextImpl;
import com.ruleforge.runtime.rete.EvaluationContextImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V6.5 — 从 KnowledgeSessionImpl 抽取的执行状态管理
 * (god class 拆分第四轮收口,延续 V6.2 SessionParameterManager / V6.3 ActivationGroupRegistry /
 * V6.4 ReteSessionRegistry 模式)。
 *
 * <p>管四块状态 (全部跟"一次 fireRules 执行"的生命周期强相关):
 * <ul>
 *   <li>{@code context} — {@link Context} 实现, 持有 allVariableCategoryMap + execMessageItems,
 *       Rule LHS 求值时读 variable category 查 variable name</li>
 *   <li>{@code evaluationContext} — {@link EvaluationContextImpl}, RETE evaluation 用
 *       (criteria value / part value 缓存), 每次 fact 进入 rete 前 clean</li>
 *   <li>{@code agenda} — {@link Agenda}, 装激活 + 顺序执行, fireRules 主路径</li>
 *   <li>{@code execMessageItems} — 调试消息列表, 给 DebugWriter 序列化
 *       (外部 console-app / executor-app 通过 {@code getExecMessageItems()} 访问)</li>
 * </ul>
 *
 * <p>KnowledgeSessionImpl 公开 API 上 {@code getContext()} / {@code getExecMessageItems()}
 * 仍由主类提供, 内部委托本类。{@link KnowledgeSessionImpl#getContext()} 外部未访问,
 * 但内部 {@code ContextImpl(this, ...)} / {@code EvaluationContextImpl(this, ...)} 构造需要本类
 * 持有以便 {@code Agenda} 构造时引用。
 */
public class ExecutionState {
    private Context context;
    private EvaluationContextImpl evaluationContext;
    private Agenda agenda;
    private final List<MessageItem> execMessageItems = new ArrayList<>();

    /**
     * KnowledgeSession 构造器 init 调 — 计算 allVariableCategoryMap (各 package map merge),
     * 创建 context / evaluationContext / agenda。
     */
    public void init(List<KnowledgePackage> knowledgePackages, KnowledgeSessionImpl owner) {
        Map<String, String> allVariableCategoryMap = null;

        for (KnowledgePackage knowledgePackage : knowledgePackages) {
            if (allVariableCategoryMap == null) {
                allVariableCategoryMap = knowledgePackage.getVariableCateogoryMap();
            } else {
                allVariableCategoryMap.putAll(knowledgePackage.getVariableCateogoryMap());
            }
        }

        this.context = new ContextImpl(owner, allVariableCategoryMap, this.execMessageItems);
        this.evaluationContext = new EvaluationContextImpl(owner, allVariableCategoryMap, this.execMessageItems);
        this.agenda = new Agenda(this.context);
    }

    /** initFromParentSession: 子会话复用父会话的 execMessageItems 引用 (V6.5 保留原 L82 字段重赋值语义)。 */
    public void inheritExecMessageItemsFromParent(List<MessageItem> parentExecMessageItems) {
        this.execMessageItems.clear();
        this.execMessageItems.addAll(parentExecMessageItems);
    }

    /** writeLogFile 后清空 (原 L379 行为)。 */
    public void clearExecMessageItems() {
        this.execMessageItems.clear();
    }

    public Context getContext() {
        return this.context;
    }

    public EvaluationContextImpl getEvaluationContext() {
        return this.evaluationContext;
    }

    public Agenda getAgenda() {
        return this.agenda;
    }

    public List<MessageItem> getExecMessageItems() {
        return this.execMessageItems;
    }
}