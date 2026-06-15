package com.ruleforge.runtime;

import com.ruleforge.runtime.rete.Context;

import java.util.List;
import java.util.Map;


/**
 * @author Jacky.gao
 * @since 2015年1月8日
 */
public interface WorkingMemory extends EventManager {
    boolean insert(Object var1);

    void assertFact(Object var1);

    boolean update(Object var1);

    boolean retract(Object var1);

    Object getParameter(String var1);

    Map<String, Object> getParameters();

    /**
     * @deprecated V5.82:此方法返 last-wins Map 视图(同 className 后插的 fact 覆盖前插的),
     *     不反映 session 实际持有的全部 fact。引擎路径(fireRules / activeRule /
     *     activeAgendaGroup)改走 {@link #getAllFactsList()}。
     *     <p>本方法保留仅供以下调用点使用 — 它们契约都是"className 命中一个 fact 即可":
     *     <ul>
     *       <li>{@code ValueCompute.findObject}(同 pattern 跨 fact 引用查找)</li>
     *       <li>{@code LoopRule}(loop body 内按 className 找其他 fact)</li>
     *       <li>{@code KnowledgeSessionTest:265}({@code containsEntry} 断言)</li>
     *     </ul>
     *     新代码应使用 {@link #getAllFactsList()}。
     */
    @Deprecated
    Map<String, Object> getAllFactsMap();

    /**
     * V5.82 — 返 session 持有的全部 fact 列表(同 className 多 fact 累加,不覆盖)。
     *
     * <p>引擎 fireRules / activeRule / activeAgendaGroup / retract 走本路径,不再走
     * {@link #getAllFactsMap()} 的 last-wins Map 视图 — 修 {@code allFactsMap}
     * {@code Map<String,Object>} className-keyed 覆盖 pre-existing bug(见
     * [[v581-criteria-test-wiring-fix]] TD-19.5)。
     */
    List<Object> getAllFactsList();

    KnowledgeSession getKnowledgeSession(String var1);

    void putKnowledgeSession(String var1, KnowledgeSession var2);

    void setSessionValue(String var1, Object var2);

    Object getSessionValue(String var1);

    Map<String, Object> getSessionValueMap();

    void activeRule(String var1, String var2);

    void activeAgendaGroup(String var1);

    Context getContext();

}
