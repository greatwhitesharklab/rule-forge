package com.ruleforge.model.rete;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author fred
 * 2018-11-05 6:15 PM
 */
@Data
@NoArgsConstructor
public class ReteUnit {
    private String ruleName;
    private Date effectiveDate;
    private Date expiresDate;
    private Rete rete;

    public ReteUnit(Rete rete, String ruleName) {
        this.rete = rete;
        this.ruleName = ruleName;
    }
}
