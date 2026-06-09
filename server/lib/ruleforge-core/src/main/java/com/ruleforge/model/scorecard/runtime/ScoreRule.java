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
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.rete.Context;
import com.ruleforge.runtime.rete.ValueCompute;
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

                RowItemImpl rowItemImpl;
                if (rowMap.containsKey(rowNumber)) {
                    rowItemImpl = rowMap.get(rowNumber);
                } else {
                    rowItemImpl = new RowItemImpl();
                    rowItemImpl.setRowNumber(rowNumber);
                    rowMap.put(rowNumber, rowItemImpl);
                }

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

            ScoringStrategy scoringStrategy = (ScoringStrategy) context.getApplicationContext().getBean(this.scoringBean);
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
