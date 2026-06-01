package com.ruleforge.parse;

import org.dom4j.Element;

/**
 * @author Jacky.gao
 * @author fred
 * 2014年12月23日
 */
public interface Parser<T> {
    boolean support(String name);

    T parse(Element element);
}
