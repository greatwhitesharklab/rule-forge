package com.ruleforge.model.table;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ruleforge.model.rule.Library;

/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class DecisionTable {
	private Integer salience;
	private Date effectiveDate;
	private Date expiresDate;
	private Boolean enabled;
	private Boolean debug;
	private String remark;
	private List<Row> rows;
	private List<Column> columns;
	private Map<String,Cell> cellMap;
	private List<Library> libraries;
	/**
	 * @since 5.40 — DMN 1.3 决策表 hit policy,跟 .dmn 的 {@code <decisionTable hitPolicy="...">} 对应。
	 * 老 .xml 路径无此概念,默认 {@code null}(语义等价 {@link HitPolicy#FIRST} 首行命中)。
	 */
	private HitPolicy hitPolicy;
	/**
	 * @since 5.40 — DMN 1.3 决策表 aggregation,跟 .dmn 的 {@code <decisionTable aggregation="...">} 对应。
	 * 老 .xml 路径无此概念,默认 {@code null}。
	 */
	private Aggregation aggregation;
	/**
	 * @since 5.40 — IR 来源方言,默认 {@link TableDialect#RULEFORGE_NATIVE} 保持 V5.39 兼容。
	 * V5.40+ 写的新决策表走 {@link TableDialect#DMN}。
	 */
	private TableDialect dialect;
	/**
	 * @since 5.40 — 决策表 variable name(DMN 的 {@code <variable name="..."/>}),
	 * 用作 evaluateAll 输出 key。RuleForge 老 .xml 用 decisionTable 节点 name 属性。
	 */
	private String variableName;
	
	public Integer getSalience() {
		return salience;
	}
	public void setSalience(Integer salience) {
		this.salience = salience;
	}
	public Date getEffectiveDate() {
		return effectiveDate;
	}
	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}
	public Date getExpiresDate() {
		return expiresDate;
	}
	public void setExpiresDate(Date expiresDate) {
		this.expiresDate = expiresDate;
	}
	public Boolean getEnabled() {
		return enabled;
	}
	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
	public Boolean getDebug() {
		return debug;
	}
	public void setDebug(Boolean debug) {
		this.debug = debug;
	}
	public String getRemark() {
		return remark;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	public List<Row> getRows() {
		return rows;
	}
	public void addLibrary(Library library){
		if(libraries==null){
			libraries=new ArrayList<Library>();
		}
		libraries.add(library);
	}
	public void addRow(Row row){
		if(rows==null){
			rows=new ArrayList<Row>();
		}
		rows.add(row);
	}
	public void addColumn(Column col){
		if(columns==null){
			columns=new ArrayList<Column>();
		}
		columns.add(col);
	}
	public void addCell(Cell cell){
		if(cellMap==null){
			cellMap=new HashMap<String,Cell>();
		}
		cellMap.put(buildCellKey(cell.getRow(),cell.getCol()), cell);
	}
	public void setRows(List<Row> rows) {
		this.rows = rows;
	}
	public List<Column> getColumns() {
		return columns;
	}
	public void setColumns(List<Column> columns) {
		this.columns = columns;
	}
	public Map<String, Cell> getCellMap() {
		return cellMap;
	}
	public List<Library> getLibraries() {
		return libraries;
	}
	public void setLibraries(List<Library> libraries) {
		this.libraries = libraries;
	}
	public String buildCellKey(int row,int col){
		return row+","+col;
	}
	public HitPolicy getHitPolicy() {
		return hitPolicy;
	}
	public void setHitPolicy(HitPolicy hitPolicy) {
		this.hitPolicy = hitPolicy;
	}
	public Aggregation getAggregation() {
		return aggregation;
	}
	public void setAggregation(Aggregation aggregation) {
		this.aggregation = aggregation;
	}
	public TableDialect getDialect() {
		return dialect;
	}
	public void setDialect(TableDialect dialect) {
		this.dialect = dialect;
	}
	public String getVariableName() {
		return variableName;
	}
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}
}
