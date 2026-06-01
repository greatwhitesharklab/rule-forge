package com.ruleforge.runtime.builtinaction;

import java.util.Map;

import com.ruleforge.model.library.action.annotation.ActionBean;
import com.ruleforge.model.library.action.annotation.ActionMethod;
import com.ruleforge.model.library.action.annotation.ActionMethodParameter;

/**
 * @author Jacky.gao
 * @since 2016年6月21日
 */
@ActionBean(name="Map集合")
public class MapAction {
	
	@ActionMethod(name="添加到Map")
	@ActionMethodParameter(names={"Map对象","key","value"})
	public void put(Map<String,Object> map,String key,Object value){
		map.put(key, value);
	}
	@ActionMethod(name="从Map中删除")
	@ActionMethodParameter(names={"Map对象","key"})
	public void remove(Map<String,Object> map,String key){
		map.remove(key);
	}
	@ActionMethod(name="指定Key是否存在")
	@ActionMethodParameter(names={"Map对象","key"})
	public boolean containsKey(Map<String,Object> map,String key){
		return map.containsKey(key);
	}
	@ActionMethod(name="从Map中取值")
	@ActionMethodParameter(names={"Map对象","key"})
	public Object get(Map<String,Object> map,String key){
		return map.get(key);
	}
	@ActionMethod(name="返回Map大小")
	@ActionMethodParameter(names={"Map对象"})
	public int size(Map<String,Object> map){
		return map.size();
	}
}
