package com.ruleforge.model.scorecard.runtime;

import com.ruleforge.Utils;
import com.ruleforge.action.ActionValue;
import com.ruleforge.debug.MsgType;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.scorecard.AssignTargetType;
import com.ruleforge.model.scorecard.ScoringType;
import com.ruleforge.runtime.EngineContext;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.engine.Context;
import com.ruleforge.engine.ValueCompute;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreRule extends Rule {
    private ScoringType scoringType;
    private String scoringBean;
    private AssignTargetType assignTargetType;
    private String variableCategory;
    private String variableName;
    private String variableLabel;
    private Datatype datatype;
    @JsonIgnore
    private List<Library> libraries;
    private KnowledgePackageWrapper knowledgePackageWrapper;
    private final Log log = LogFactory.getLog(this.getClass());

    public ScoreRule() {
    }

    public List<ActionValue> execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
        KnowledgeSession parentSession = (KnowledgeSession) context.getWorkingMemory();
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(this.knowledgePackageWrapper, context, parentSession);
        boolean isdebug = false;
        if (this.getDebug() != null) {
            isdebug = this.getDebug();
        }

        List<ActionValue> values = session.fireRules(parentSession.getParameters()).getActionValues();
        Map<Integer, RowItemImpl> rowMap = new HashMap<>();

        for (ActionValue value : values) {
            if (value.getValue() instanceof ScoreRuntimeValue) {
                ScoreRuntimeValue scoreValue = (ScoreRuntimeValue) value.getValue();
                int rowNumber = scoreValue.getRowNumber();
                String rowItem;
                rowItem = "--- 行" + rowNumber + ",得分：" + scoreValue.getValue();
                context.logMsg(rowItem, MsgType.ScoreCard);

                RowItemImpl rowItemImpl = getOrCreateRow(rowMap, rowNumber);

                if (scoreValue.getName().equals("scoring_value")) {
                    rowItemImpl.setScore(scoreValue.getValue());
                    rowItemImpl.setWeight(scoreValue.getWeight());
                } else {
                    CellItem cellItem = new CellItem(scoreValue.getName(), scoreValue.getValue());
                    rowItemImpl.addCellItem(cellItem);
                }
            }

        }

        List<RowItem> items = new ArrayList<>(rowMap.values().size());
        items.addAll(rowMap.values());
        ScorecardImpl card = new ScorecardImpl(this.getName(), items, isdebug);
        Object actualScore = null;
        String msg;
        if (this.scoringType.equals(ScoringType.sum)) {
            actualScore = card.executeSum(context);
        } else if (this.scoringType.equals(ScoringType.weightsum)) {
            actualScore = card.executeWeightSum(context);
        } else if (this.scoringType.equals(ScoringType.custom)) {
            msg = "--- 执行自定义评分卡得分计算Bean:" + this.scoringBean;
            context.logMsg(msg, MsgType.ScoreCard);

            ScoringStrategy scoringStrategy = (ScoringStrategy) EngineContext.getBean(this.scoringBean);
            actualScore = scoringStrategy.calculate(card, context);
        }

        if (this.assignTargetType.equals(AssignTargetType.none)) {
            this.log.warn("Scorecard [" + card.getName() + "] not setting assignment object for score value, score value is :" + actualScore);
        } else {
            ValueCompute valueCompute = context.getValueCompute();
            String className = context.getVariableCategoryClass(this.variableCategory);
            Object targetFact;
            if (className.equals(HashMap.class.getName())) {
                targetFact = session.getParameters();
            } else {
                targetFact = valueCompute.findObject(className, matchedObject, context);
            }

            if (targetFact == null) {
                throw new RuleException("Class[" + className + "] not found in workingmemory.");
            }

            actualScore = this.datatype.convert(actualScore);
            Utils.setObjectProperty(targetFact, this.variableName, actualScore);
        }

        parentSession.getParameters().putAll(session.getParameters());
        return null;
    }

    /**
     * V5.100.4 — 从 {@code rowMap} 取 (cache hit) 或创建 (cache miss) 指定 rowNumber 的
     * {@link RowItemImpl}, 保留到 map 中. computeIfAbsent-style cache-or-create.
     *
     * <p>砍 {@code containsKey + get} 双 lookup, 套 V5.93 原则: {@code map.get(key) == null}
     * 已能区分 absent vs null-value. 本场景 rowMap 唯一 put 是 {@code put(rowNumber,
     * new RowItemImpl())} (永不为 null, 无 {@code put(key, null)} 风险), 所以 {@code get == null}
     * ↔ {@code !containsKey} 100% 等价. 节省 1 个 containsKey hash lookup per repeat-row
     * ActionValue (runtime per-scorecard-eval, 比 build-time 频, JFR noise level 预期).
     *
     * <p>从 {@code execute()} 抽出为独立 static helper — 既是 cache-or-create 的纯函数封装
     * (缩短 execute()), 又让 V5.100.4 逻辑可单测 (clean inputs: Map + int, 不依赖
     * KnowledgeSessionFactory / Context 等重装配).
     *
     * @param rowMap     execute 局部 row-number → RowItemImpl 缓存 (非 null)
     * @param rowNumber  评分卡行号
     * @return 该 rowNumber 对应的 RowItemImpl (cache hit 复用 / cache miss 新建并装入 map)
     */
    private static RowItemImpl getOrCreateRow(Map<Integer, RowItemImpl> rowMap, int rowNumber) {
        RowItemImpl rowItemImpl = rowMap.get(rowNumber);
        if (rowItemImpl == null) {
            rowItemImpl = new RowItemImpl();
            rowItemImpl.setRowNumber(rowNumber);
            rowMap.put(rowNumber, rowItemImpl);
        }
        return rowItemImpl;
    }

    public ScoringType getScoringType() {
        return this.scoringType;
    }

    public void setScoringType(ScoringType scoringType) {
        this.scoringType = scoringType;
    }

    public String getScoringBean() {
        return this.scoringBean;
    }

    public void setScoringBean(String scoringBean) {
        this.scoringBean = scoringBean;
    }

    public AssignTargetType getAssignTargetType() {
        return this.assignTargetType;
    }

    public void setAssignTargetType(AssignTargetType assignTargetType) {
        this.assignTargetType = assignTargetType;
    }

    public String getVariableCategory() {
        return this.variableCategory;
    }

    public void setVariableCategory(String variableCategory) {
        this.variableCategory = variableCategory;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableLabel() {
        return this.variableLabel;
    }

    public void setVariableLabel(String variableLabel) {
        this.variableLabel = variableLabel;
    }

    public Datatype getDatatype() {
        return this.datatype;
    }

    public void setDatatype(Datatype datatype) {
        this.datatype = datatype;
    }

    public List<Library> getLibraries() {
        return this.libraries;
    }

    public void setLibraries(List<Library> libraries) {
        this.libraries = libraries;
    }

    public KnowledgePackageWrapper getKnowledgePackageWrapper() {
        return this.knowledgePackageWrapper;
    }

    public void setKnowledgePackageWrapper(KnowledgePackageWrapper knowledgePackageWrapper) {
        this.knowledgePackageWrapper = knowledgePackageWrapper;
    }
}
