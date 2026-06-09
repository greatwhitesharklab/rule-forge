package com.ruleforge.console.repository.refactor;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BeanItem implements Item {
    private String oldBeanId;
    private String newBeanId;
    private String oldBeanLabel;
    private String newBeanLabel;
}

