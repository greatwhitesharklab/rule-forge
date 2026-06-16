package com.ruleforge.runtime.rete;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.runtime.WorkingMemory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluationContextImpl extends ContextImpl implements EvaluationContext {
    private Activity prevActivity;
    private Map<String, Object> criteriaValueMap = new HashMap<>();
    private Map<String, Object> partValueMap = new HashMap<>();

    public EvaluationContextImpl(WorkingMemory workingMemory,
                                 Map<String, String> variableCategoryMap, List<MessageItem> debugMessageItems) {
        super(workingMemory, variableCategoryMap, debugMessageItems);
    }

    @Override
    public Activity getPrevActivity() {
        return prevActivity;
    }

    @Override
    public void setPrevActivity(Activity activity) {
        this.prevActivity = activity;
    }

    @Override
    public Object getCriteriaValue(String id) {
        // V5.93 — 去掉冗余 containsKey 检查,HashMap.get 已对 missing key 返 null。
        // 旧实现每 call 2 次 HashMap op(containsKey + get),现在 1 次。
        // per-fact 每 criteria 跑一次,省 1 HashMap op + 1 String.hashCode,
        // 预期 post-V5.92 JFR String.hashCode 546 sample -50%(本路径占大头)。
        // 行为等价:HashMap.get 对 "key 不存在" 和 "key 存在但 null 值" 都返 null,
        // 两种 case 在 HashMap 语义上不可区分 — 锁 [[v593-evaluationcontext-double-lookup]]。
        return criteriaValueMap.get(id);
    }

    @Override
    public Object getPartValue(String id) {
        return partValueMap.get(id);
    }

    @Override
    public void storeCriteriaValue(String id, Object obj) {
        criteriaValueMap.put(id, obj);
    }

    @Override
    public void storePartValue(String id, Object obj) {
        partValueMap.put(id, obj);
    }

    @Override
    public boolean partValueExist(String id) {
        return partValueMap.containsKey(id);
    }

    public void clean() {
        criteriaValueMap.clear();
        partValueMap.clear();
    }
}
