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
        // V5.90 — 默认 debug=false 让 V5.88 早返 (CriteriaActivity.logMessage) 默认生效。
        // V5.88 fix 100% 正确但 bench/measure 一直没兑现:HotPathBenchTest buildDualClassRule
        // 用 new Rule() 直接构造,Rule 旧默认 debug=true 让早返跳过,String.format 每次
        // evaluate 跑。生产路径同样问题:test fixture + console-app service layer 等所有
        // program-built Rule 都付这个 cost。XML 路径仍可走 <rule debug="true"> 显式开
        // (AbstractRuleParser.java:46-48)。所有 isDebug/getDebug consumer 都是 observability,
        // 无 control flow 依赖(详见 V5.90 doc audit)。
        this.debug = false;
    }
}
