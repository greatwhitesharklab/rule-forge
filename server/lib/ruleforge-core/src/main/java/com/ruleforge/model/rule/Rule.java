package com.ruleforge.model.rule;

import com.ruleforge.model.rule.lhs.Lhs;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

@Data
public class Rule implements RuleInfo {
    private String id;
    private String name;
    private RuleType ruleType;
    private String file;
    private Integer salience;
    private Date effectiveDate;
    private Date expiresDate;
    private Boolean enabled;
    private Boolean debug;
    private String activationGroup;
    private String agendaGroup;
    private Boolean autoFocus;
    private String ruleflowGroup;
    private Lhs lhs;
    private Rhs rhs;
    private Other other;
    private Boolean loop;
    private Boolean loopRule = false;
    private String remark;
    private boolean withElse;
    @JsonIgnore
    private Rule elseRule;

    public Rule() {
        this.debug = true;
    }
}
