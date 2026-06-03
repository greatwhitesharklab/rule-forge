package com.ruleforge.console.repository.refactor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class ConstItem extends FieldItem {
    private String category;
}
