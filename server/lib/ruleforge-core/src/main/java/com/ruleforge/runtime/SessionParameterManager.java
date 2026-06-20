package com.ruleforge.runtime;

import com.ruleforge.model.library.Datatype;

import java.util.*;

/**
 * V6.2 — 从 KnowledgeSessionImpl 抽取的会话参数管理(god class 拆分)。
 * 管 sessionValueMap / parameterMap / initParameters 三张 map。
 */
public class SessionParameterManager {
    private final Map<String, Object> sessionValueMap = new HashMap<>();
    private final Map<String, Object> initParameters = new HashMap<>();
    private final Map<String, Object> parameterMap = new HashMap<>();

    public void initFromKnowledgePackage(KnowledgePackage knowledgePackage) {
        if (knowledgePackage == null) return;
        Map<String, String> params = knowledgePackage.getParameters();
        if (params == null) return;
        for (String key : params.keySet()) {
            putDefaultParameter(key, Datatype.valueOf(params.get(key)));
        }
    }

    public void initFromParentSessionValueMap(Map<String, Object> parentSessionValueMap) {
        this.sessionValueMap.putAll(parentSessionValueMap);
    }

    /** V5.96 逻辑保留:List/Set/Map 清空、Number→0、Boolean→false、String 移除。 */
    public void clearInitParameters() {
        List<String> stringList = new ArrayList<>();
        for (String key : this.initParameters.keySet()) {
            Object obj = this.initParameters.get(key);
            if (obj != null) {
                if (obj instanceof List) {
                    ((List) obj).clear();
                } else if (obj instanceof Set) {
                    ((Set) obj).clear();
                } else if (obj instanceof Map) {
                    ((Map) obj).clear();
                } else if (obj instanceof Number) {
                    this.initParameters.put(key, 0);
                } else if (obj instanceof Boolean) {
                    this.initParameters.put(key, false);
                } else if (obj instanceof String) {
                    stringList.add(key);
                }
            }
        }
        for (String key : stringList) {
            this.initParameters.remove(key);
        }
    }

    /** 按声明类型给参数放默认值(与原构造器 if-else 链等价)。 */
    public void putDefaultParameter(String key, Datatype type) {
        Object defaultValue;
        switch (type) {
            case Integer:
            case Long:
            case Double:
            case Float:
                defaultValue = 0;
                break;
            case Boolean:
                defaultValue = false;
                break;
            case List:
                defaultValue = new ArrayList<>();
                break;
            case Set:
                defaultValue = new HashSet<>();
                break;
            case Map:
                defaultValue = new HashMap<>();
                break;
            default:
                return;
        }
        this.initParameters.put(key, defaultValue);
    }

    /** fireRules 前准备:清参数 → 回填 init → 合并 fact maps → 合并 runtime params。 */
    public void prepareForExecution(Map<String, Object> runtimeParams, List<Map<?, ?>> factMaps) {
        this.parameterMap.clear();
        clearInitParameters();
        this.parameterMap.putAll(this.initParameters);
        for (Map<?, ?> factMap : factMaps) {
            for (Object key : factMap.keySet()) {
                this.parameterMap.put(key.toString(), factMap.get(key));
            }
        }
        if (runtimeParams != null) {
            this.parameterMap.putAll(runtimeParams);
        }
    }

    public Object getParameter(String key) {
        return this.parameterMap.get(key);
    }

    public Map<String, Object> getParameters() {
        return this.parameterMap;
    }

    public Map<String, Object> getSessionValueMap() {
        return this.sessionValueMap;
    }

    /** V6.2 — test 用反射访问 initParameters(原 KnowledgeSessionImpl 字段)。 */
    public Map<String, Object> getInitParameters() {
        return this.initParameters;
    }
    public Map<String, Object> getParameterMap() {
        return this.parameterMap;
    }
}
