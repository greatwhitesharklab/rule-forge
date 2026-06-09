package com.ruleforge.console.repository.refactor;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BeanMethodItem implements Item {
    private String beanId;
    private String beanLabel;
    private String oldMethodName;
    private String newMethodName;
    private String oldMethodLabel;
    private String newMethodLabel;
}

