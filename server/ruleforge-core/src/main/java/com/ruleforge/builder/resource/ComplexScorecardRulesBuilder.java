package com.ruleforge.builder.resource;

import com.ruleforge.action.ScoringAction;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.builder.table.CellContentBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.scorecard.ComplexColumn;
import com.ruleforge.model.scorecard.ComplexColumnType;
import com.ruleforge.model.scorecard.ComplexScorecardDefinition;
import com.ruleforge.model.scorecard.runtime.ScoreRule;
import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Row;
import com.ruleforge.parse.deserializer.ComplexScorecardDeserializer;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.dom4j.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author fred
 */
@Setter
@NoArgsConstructor
public class ComplexScorecardRulesBuilder implements ResourceBuilder<ScoreRule> {
    private ReteBuilder reteBuilder;
    private ResourceLibraryBuilder resourceLibraryBuilder;
    private RulesRebuilder rulesRebuilder;
    private CellContentBuilder cellContentBuilder;
    private ComplexScorecardDeserializer complexScorecardDeserializer;

    public ScoreRule build(Element root) {
        ComplexScorecardDefinition scorecard = this.complexScorecardDeserializer.deserialize(root);
        ScoreRule scoreRule = new ScoreRule();
        scoreRule.setName("scc");
        scoreRule.setEffectiveDate(scorecard.getEffectiveDate());
        scoreRule.setExpiresDate(scorecard.getExpiresDate());
        scoreRule.setEnabled(scorecard.getEnabled());
        scoreRule.setSalience(scorecard.getSalience());
        scoreRule.setDebug(scorecard.getDebug());
        scoreRule.setScoringBean(scorecard.getScoringBean());
        scoreRule.setScoringType(scorecard.getScoringType());
        scoreRule.setAssignTargetType(scorecard.getAssignTargetType());
        scoreRule.setDatatype(scorecard.getDatatype());
        scoreRule.setVariableCategory(scorecard.getVariableCategory());
        scoreRule.setVariableName(scorecard.getVariableName());
        scoreRule.setVariableLabel(scorecard.getVariableLabel());
        scoreRule.setLibraries(scorecard.getLibraries());
        List<Rule> rules = new ArrayList<>();
        List<Row> rows = scorecard.getRows();
        List<ComplexColumn> columns = scorecard.getColumns();

        for (Row row : rows) {
            Rule rule = new Rule();
            rule.setDebug(scorecard.getDebug());
            rule.setSalience(scorecard.getSalience());
            rule.setExpiresDate(scorecard.getExpiresDate());
            rule.setEffectiveDate(scorecard.getEffectiveDate());
            rule.setEnabled(scorecard.getEnabled());
            rule.setName("sccr" + row.getNum());
            Lhs lhs = new Lhs();
            And and = new And();
            rule.setLhs(lhs);
            Rhs rhs = new Rhs();
            rule.setRhs(rhs);
            rules.add(rule);
            Value value = null;

            for (ComplexColumn col : columns) {
                Cell cell = getCell(scorecard, row.getNum(), col.getNum());
                ComplexColumnType type = col.getType();
                ScoringAction action;
                switch (type) {
                    case Criteria:
                        Criterion criterion = this.cellContentBuilder.buildCriterion(cell, col);
                        if (criterion != null) {
                            and.addCriterion(criterion);
                        }
                        break;
                    case Score:
                        value = cell.getValue();
                        if (value != null) {
                            action = new ScoringAction(row.getNum(), "scoring_value", null);
                            action.setValue(value);
                            rhs.addAction(action);
                        }
                        break;
                    case Custom:
                        value = cell.getValue();
                        if (value != null) {
                            action = new ScoringAction(row.getNum(), col.getCustomLabel(), null);
                            action.setValue(value);
                            rhs.addAction(action);
                        }
                }
            }

            if (and.getCriterions() != null && !and.getCriterions().isEmpty()) {
                lhs.setCriterion(and);
            }
        }

        this.rulesRebuilder.rebuildRules(scorecard.getLibraries(), rules);
        ResourceLibrary resourceLibrary = this.resourceLibraryBuilder.buildResourceLibrary(scorecard.getLibraries());
        Rete rete = this.reteBuilder.buildRete(rules, resourceLibrary);
        KnowledgeBase base = new KnowledgeBase(rete);
        KnowledgePackageWrapper knowledgePackageWrapper = new KnowledgePackageWrapper(base.getKnowledgePackage());
        scoreRule.setKnowledgePackageWrapper(knowledgePackageWrapper);
        return scoreRule;
    }

    private Cell getCell(ComplexScorecardDefinition table, int row, int column) {
        Map<String, Cell> cellMap = table.getCellMap();
        Cell cell = null;

        for (int i = row; i > -1; --i) {
            String key = table.buildCellKey(i, column);
            if (cellMap.containsKey(key)) {
                cell = cellMap.get(key);
                break;
            }
        }

        if (cell == null) {
            throw new RuleException("Decision table cell[" + row + "," + column + "] not exist.");
        } else {
            return cell;
        }
    }

    public boolean support(Element root) {
        return this.complexScorecardDeserializer.support(root);
    }

    public ResourceType getType() {
        return ResourceType.ComplexScorecard;
    }
}
