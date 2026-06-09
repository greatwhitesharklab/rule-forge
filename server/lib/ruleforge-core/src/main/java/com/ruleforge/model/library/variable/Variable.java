package com.ruleforge.model.library.variable;

import com.ruleforge.model.library.Datatype;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Jacky.gao
 * @author Fred
 * @since 2014年12月23日
 */
@Data
public class Variable implements Serializable {

    private String name;
    private String label;
    private Datatype type;
    private String defaultValue;
    private Act act;

    /**
     * 20250701新增字段
     */
    private Integer dsStatus;
    private String logicComment;
    private String categoryLabel;
}
