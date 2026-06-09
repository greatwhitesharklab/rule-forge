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
}
