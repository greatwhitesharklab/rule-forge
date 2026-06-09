package com.ruleforge.model;

import com.ruleforge.exception.RuleException;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;

/**
 * @author Jacky.gao
 * 2016年6月2日
 */
public class GeneralEntity extends HashMap<String, Object> {
    private static final long serialVersionUID = 2778576006420277518L;
    private String targetClass;

    public GeneralEntity(String targetClass) {
        if (StringUtils.isBlank(targetClass)) {
            throw new RuleException("Target class cannot be null.");
        }
        this.targetClass = targetClass;
    }

    public String getTargetClass() {
        return targetClass;
    }

    @Override
    public boolean equals(Object other) {
        boolean classEquals = false;
        if (other instanceof GeneralEntity) {
            GeneralEntity entity = (GeneralEntity) other;
            if (targetClass.equals(entity.getTargetClass())) {
                classEquals = true;
            }
        }
        if (classEquals) {
            return super.equals(other);
        }
        return false;
    }
}
