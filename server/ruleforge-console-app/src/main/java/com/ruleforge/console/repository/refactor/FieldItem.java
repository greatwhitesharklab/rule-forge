package com.ruleforge.console.repository.refactor;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public abstract class FieldItem implements Item {
    private String newName;
    private String newLabel;
    private String oldName;
    private String oldLabel;
}

