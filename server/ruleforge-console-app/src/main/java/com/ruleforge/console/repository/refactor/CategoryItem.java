package com.ruleforge.console.repository.refactor;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CategoryItem implements Item {
    private String oldCategory;
    private String newCategory;
}

