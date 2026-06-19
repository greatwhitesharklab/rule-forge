package com.ruleforge.runtime.rete;
import com.ruleforge.engine.Activity;
import com.ruleforge.engine.EvaluationContext;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.engine.WorkingMemory;

import java.util.List;
import java.util.Map;

public class EvaluationContextImpl extends ContextImpl implements EvaluationContext {
    private Activity prevActivity;
    // V5.98 — 2-array small store 替代 HashMap。
    // 典型 per-fact N=0-5(criteria 数),linear scan 胜 HashMap(hash + bucket + equals 链)。
    // 超 8 自动 grow。Inline initial capacity = 8 覆盖 ~95% case。
    // JFR V5.97 显示 HashMap.get/put/clear 在 rete hot path 占 ~779 sample
    // (getCriteriaValue 437 + storePartValue 156 + clean 186),本改造预期 -90% 以上。
    // 行为等价:linear scan 找不到 ↔ HashMap.get miss ↔ HashMap.containsKey==false,100% 等价。
    // Locked by [[v598-evaluation-context-2array]] BDD tests。
    private static final int INLINE_CAPACITY = 8;
    private String[] criteriaKeys = new String[INLINE_CAPACITY];
    private Object[] criteriaValues = new Object[INLINE_CAPACITY];
    private int criteriaSize = 0;
    private String[] partKeys = new String[INLINE_CAPACITY];
    private Object[] partValues = new Object[INLINE_CAPACITY];
    private int partSize = 0;

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
        for (int i = 0; i < criteriaSize; i++) {
            if (id.equals(criteriaKeys[i])) {
                return criteriaValues[i];
            }
        }
        return null;
    }

    @Override
    public Object getPartValue(String id) {
        for (int i = 0; i < partSize; i++) {
            if (id.equals(partKeys[i])) {
                return partValues[i];
            }
        }
        return null;
    }

    @Override
    public void storeCriteriaValue(String id, Object obj) {
        for (int i = 0; i < criteriaSize; i++) {
            if (id.equals(criteriaKeys[i])) {
                criteriaValues[i] = obj;
                return;
            }
        }
        if (criteriaSize == criteriaKeys.length) {
            growCriteria();
        }
        criteriaKeys[criteriaSize] = id;
        criteriaValues[criteriaSize] = obj;
        criteriaSize++;
    }

    @Override
    public void storePartValue(String id, Object obj) {
        for (int i = 0; i < partSize; i++) {
            if (id.equals(partKeys[i])) {
                partValues[i] = obj;
                return;
            }
        }
        if (partSize == partKeys.length) {
            growPart();
        }
        partKeys[partSize] = id;
        partValues[partSize] = obj;
        partSize++;
    }

    @Override
    public boolean partValueExist(String id) {
        for (int i = 0; i < partSize; i++) {
            if (id.equals(partKeys[i])) {
                return true;
            }
        }
        return false;
    }

    public void clean() {
        // Reset size + 清空 array 引用(避免 GC 漏掉旧 key/value 强引用)
        for (int i = 0; i < criteriaSize; i++) {
            criteriaKeys[i] = null;
            criteriaValues[i] = null;
        }
        criteriaSize = 0;
        for (int i = 0; i < partSize; i++) {
            partKeys[i] = null;
            partValues[i] = null;
        }
        partSize = 0;
    }

    private void growCriteria() {
        int newCap = criteriaKeys.length * 2;
        String[] newKeys = new String[newCap];
        Object[] newValues = new Object[newCap];
        System.arraycopy(criteriaKeys, 0, newKeys, 0, criteriaSize);
        System.arraycopy(criteriaValues, 0, newValues, 0, criteriaSize);
        criteriaKeys = newKeys;
        criteriaValues = newValues;
    }

    private void growPart() {
        int newCap = partKeys.length * 2;
        String[] newKeys = new String[newCap];
        Object[] newValues = new Object[newCap];
        System.arraycopy(partKeys, 0, newKeys, 0, partSize);
        System.arraycopy(partValues, 0, newValues, 0, partSize);
        partKeys = newKeys;
        partValues = newValues;
    }
}
