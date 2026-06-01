package com.ruleforge.parse.deserializer;

import org.dom4j.Element;

/**
 * @author Jacky.gao
 * 2014年12月23日
 */
public interface Deserializer<T> {

    T deserialize(Element root);

    // todo 重构读取snapshot文件
    T deserialize(Element root, boolean isContainSnapshot);

    boolean support(Element root);
}
