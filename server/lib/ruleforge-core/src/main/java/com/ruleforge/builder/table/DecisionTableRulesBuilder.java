package com.ruleforge.builder.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ruleforge.exception.RuleException;
import com.ruleforge.action.AbstractAction;
import com.ruleforge.action.Action;
import com.ruleforge.action.ConsolePrintAction;
import com.ruleforge.action.VariableAssignAction;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.Row;

/**
 * @author Jacky.gao
 * @since 2015年1月20日
 */
public class DecisionTableRulesBuilder {
	private CellContentBuilder cellContentBuilder;
	public List<Rule> buildRules(DecisionTable table){
		List<Rule> rules=new ArrayList<Rule>();
		List<Row> rows=table.getRows();
		List<Column> columns=table.getColumns();
		if(rows==null || columns==null){
			return rules;
		}
		for(Row row:rows){
			Rule rule=new Rule();
			rule.setDebug(table.getDebug());
			rule.setSalience(table.getSalience());
			rule.setExpiresDate(table.getExpiresDate());
			rule.setEffectiveDate(table.getEffectiveDate());
			rule.setEnabled(table.getEnabled());
			rule.setName("r"+row.getNum());
			Lhs lhs=new Lhs();
			And and=new And();
			lhs.setCriterion(and);
			rule.setLhs(lhs);
			Rhs rhs=new Rhs();
			rule.setRhs(rhs);
			rules.add(rule);
			Value value=null;
			for(Column col:columns){
				Cell cell=getCell(table,row.getNum(),col.getNum());
				ColumnType type=col.getType();
				switch(type){
				case Criteria:
					Criterion criterion=cellContentBuilder.buildCriterion(cell,col);
					if(criterion!=null){
						and.addCriterion(criterion);						
					}
					break;
				case ConsolePrint:
					value=cell.getValue();
					if(value!=null){
						ConsolePrintAction consolePrintAction=new ConsolePrintAction();
						consolePrintAction.setPriority(1000-col.getNum());
						consolePrintAction.setValue(value);
						rhs.addAction(consolePrintAction);
					}
					break;
				case Assignment:
					value=cell.getValue();
					if(value!=null){
						VariableAssignAction variableAssignAction=new VariableAssignAction();
						variableAssignAction.setPriority(1000-col.getNum());
						variableAssignAction.setValue(value);
						variableAssignAction.setDatatype(col.getDatatype());
						variableAssignAction.setVariableName(col.getVariableName());
						variableAssignAction.setVariableLabel(col.getVariableLabel());
						variableAssignAction.setVariableCategory(col.getVariableCategory());
						rhs.addAction(variableAssignAction);
					}
					break;
				case ExecuteMethod:
					Action action=cell.getAction();
					if(action!=null){
						AbstractAction aa=(AbstractAction)action;
						aa.setPriority(1000-col.getNum());
						rhs.addAction(aa);
					}
					break;
				}
			}
		}
		return rules;
	}
	private Cell getCell(DecisionTable table,int row,int column){
		Map<String,Cell> cellMap=table.getCellMap();
		if(cellMap==null){
			throw new RuleException("Decision table cell["+row+","+column+"] not exist.");
		}
		Cell cell=null;
		// V5.100.2 — 砍 containsKey + get 双 lookup, 套 V5.93 原则. `map.get(key) == null`
		// 已能区分 absent vs null-value. 本场景 value 永为 Cell 对象 (非 null,
		// DecisionTable.java:108 + ScriptDecisionTable.java:44 唯一 put 是
		// `cellMap.put(buildCellKey(cell.getRow(), cell.getCol()), cell)`, cell 是
		// builder 内部的 Cell 实例, 无 put(key, null) 风险). 节省 1 个 containsKey hash
		// lookup per iter (build-time 调用, per-DRL-parse, 频度低, 跟 V5.100 / V5.100.1
		// 同档 pure code elegance closure).
		for(int i=row;i>-1;i--){
			String key=table.buildCellKey(i,column);
			cell=cellMap.get(key);
			if(cell!=null){
				break;
			}
		}
		if(cell==null){
			throw new RuleException("Decision table cell["+row+","+column+"] not exist.");
		}
		return cell;
	}
	public void setCellContentBuilder(CellContentBuilder cellContentBuilder) {
		this.cellContentBuilder = cellContentBuilder;
	}
}
