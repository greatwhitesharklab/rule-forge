package com.ruleforge.model.function;
/**
 * @author Jacky.gao
 * @since 2015年7月26日
 */
public class Argument {
	/**
	 * 函数第一个参数提示名称
	 */
	private String name;
	/**
	 * 当前函数是否需要第二个参数，也就是说是否需要选择属性名
	 */
	private boolean needProperty;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean isNeedProperty() {
		return needProperty;
	}
	public void setNeedProperty(boolean needProperty) {
		this.needProperty = needProperty;
	}
}
