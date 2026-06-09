package com.ruleforge.model.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ruleforge.model.rule.Library;

/**
 * @author Jacky.gao
 * @since 2015年5月5日
 */
public class ScriptDecisionTable {
	private List<Row> rows;
	private List<Column> columns;
	private Map<String,ScriptCell> cellMap;
	private List<Library> libraries;
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
	public void addCell(ScriptCell cell){
		if(cellMap==null){
			cellMap=new HashMap<String,ScriptCell>();
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
	public Map<String, ScriptCell> getCellMap() {
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
