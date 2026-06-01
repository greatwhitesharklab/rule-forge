package com.ruleforge.decision.model;

import com.ruleforge.model.Label;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class OutputModel extends BaseModel implements Serializable {
    private static final long serialVersionUID = -4822225161969078344L;

    @Label("订单号")
    private String appId;
    @Label("决策结果")
    private String ruleResult = "-1";
    @Label("规则备注")
    private String ruleRemark;
    @Label("规则名称")
    private String ruleName;
    @Label("额度")
    private Double quota;
    @Label("分数")
    private Double core;
    @Label("展期标记")
    private String extensionMark;
    @Label("类型")
    private String type;
    @Label("客户风险等级")
    private String custLevel;
    @Label("总额度")
    private Double totalAmount;
    @Label("大类额度")
    private Double mainAmount;
    @Label("资金方集合")
    private List<String> fundList = new ArrayList<>();
    @Label("准入决策结果")
    private String result;
    @Label("展期期数")
    private String extensionPeriod;
    @Label("审批类型")
    private String approvalType;
    @Label("审批原因")
    private String approvalReason;
    @Label("自动回退标签字段")
    private List<String> callBackLabelList = new ArrayList<>();
    @Label("表面年费率")
    private Double annualRate;
    @Label("绿通增加")
    private Double greenwayIncrease;
    @Label("可容忍资方成本")
    private Double tolerableCosts;
    @Label("降额标签")
    private String deratingLabel;
    @Label("高通费率")
    private Double highRate;
    @Label("转大自付标签")
    private String transferSelfPaymentLabel;
    @Label("新审批原因")
    private List<String> newApprovalLabelList = new ArrayList<>();
    @Label("通过标签")
    private Integer passFlag;

    // Additional prod-specific fields
    private Integer lockDays;
    private String ifManualReview;
    private Double creditLimit;
    private String product;
    private Integer creditLimit_validDay;
    private String newCust_mainModel_charge_v1;
    private String newCust_ruleScore_charge_v1;
    private String newCust_creditLevel_charge_v1;
    private String newCust_tradeScore_charge_v1;
    private String newCust_v1_1_1_result;
    private String rule_score;
    private String adjust_coeff;
    private String creditLimit_cap;
    private String addCredit_cap;
    private String newCust_mainModel_charge_v2;
    private String newCust_ruleScore_charge_v2;
    private String newCust_creditLevel_charge_v2;
    private String newCust_income_subScore_v1_2;
    private String newCust_debt_subScore_v1_2;
    private String newCsut_lrScoreCard_0105_score;
    private String newCsut_lrScoreCard_0105_level;
    private String newCsut_lrScoreCard_0105_credit;
    private String newWithdraw_lrScoreCard_0106_score;
    private String newWithdraw_lrScoreCard_0106_level;
    private String new_score1;
    private String new_score2;
    private String new_score3;
    private String new_level1;
    private String new_level2;
    private String new_level3;
    private String old_score1;
    private String old_score2;
    private String old_score3;
    private String old_level1;
    private String old_level2;
    private String old_level3;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getRuleResult() { return ruleResult; }
    public void setRuleResult(String ruleResult) { this.ruleResult = ruleResult; }
    public String getRuleRemark() { return ruleRemark; }
    public void setRuleRemark(String ruleRemark) { this.ruleRemark = ruleRemark; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Double getQuota() { return quota; }
    public void setQuota(Double quota) { this.quota = quota; }
    public Double getCore() { return core; }
    public void setCore(Double core) { this.core = core; }
    public String getExtensionMark() { return extensionMark; }
    public void setExtensionMark(String extensionMark) { this.extensionMark = extensionMark; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCustLevel() { return custLevel; }
    public void setCustLevel(String custLevel) { this.custLevel = custLevel; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public Double getMainAmount() { return mainAmount; }
    public void setMainAmount(Double mainAmount) { this.mainAmount = mainAmount; }
    public List<String> getFundList() { return fundList; }
    public void setFundList(String fund) { this.fundList.add(fund); }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getExtensionPeriod() { return extensionPeriod; }
    public void setExtensionPeriod(String extensionPeriod) { this.extensionPeriod = extensionPeriod; }
    public String getApprovalType() { return approvalType; }
    public void setApprovalType(String approvalType) { this.approvalType = approvalType; }
    public String getApprovalReason() { return approvalReason; }
    public void setApprovalReason(String approvalReason) { this.approvalReason = approvalReason; }
    public List<String> getCallBackLabelList() { return callBackLabelList; }
    public void setCallBackLabelList(String callBackLabel) { this.callBackLabelList.add(callBackLabel); }
    public Double getAnnualRate() { return annualRate; }
    public void setAnnualRate(Double annualRate) { this.annualRate = annualRate; }
    public Double getGreenwayIncrease() { return greenwayIncrease; }
    public void setGreenwayIncrease(Double greenwayIncrease) { this.greenwayIncrease = greenwayIncrease; }
    public Double getTolerableCosts() { return tolerableCosts; }
    public void setTolerableCosts(Double tolerableCosts) { this.tolerableCosts = tolerableCosts; }
    public String getDeratingLabel() { return deratingLabel; }
    public void setDeratingLabel(String deratingLabel) { this.deratingLabel = deratingLabel; }
    public Double getHighRate() { return highRate; }
    public void setHighRate(Double highRate) { this.highRate = highRate; }
    public String getTransferSelfPaymentLabel() { return transferSelfPaymentLabel; }
    public void setTransferSelfPaymentLabel(String transferSelfPaymentLabel) { this.transferSelfPaymentLabel = transferSelfPaymentLabel; }
    public List<String> getNewApprovalLabelList() { return newApprovalLabelList; }
    public void setNewApprovalLabelList(String newApprovalLabel) { this.newApprovalLabelList.add(newApprovalLabel); }
    public Integer getPassFlag() { return passFlag; }
    public void setPassFlag(Integer passFlag) { this.passFlag = passFlag; }
    public Integer getLockDays() { return lockDays; }
    public void setLockDays(Integer lockDays) { this.lockDays = lockDays; }
    public String getIfManualReview() { return ifManualReview; }
    public void setIfManualReview(String ifManualReview) { this.ifManualReview = ifManualReview; }
    public Double getCreditLimit() { return creditLimit; }
    public void setCreditLimit(Double creditLimit) { this.creditLimit = creditLimit; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public Integer getCreditLimit_validDay() { return creditLimit_validDay; }
    public void setCreditLimit_validDay(Integer creditLimit_validDay) { this.creditLimit_validDay = creditLimit_validDay; }
    public String getNewCust_mainModel_charge_v1() { return newCust_mainModel_charge_v1; }
    public void setNewCust_mainModel_charge_v1(String v) { this.newCust_mainModel_charge_v1 = v; }
    public String getNewCust_ruleScore_charge_v1() { return newCust_ruleScore_charge_v1; }
    public void setNewCust_ruleScore_charge_v1(String v) { this.newCust_ruleScore_charge_v1 = v; }
    public String getNewCust_creditLevel_charge_v1() { return newCust_creditLevel_charge_v1; }
    public void setNewCust_creditLevel_charge_v1(String v) { this.newCust_creditLevel_charge_v1 = v; }
    public String getNewCust_tradeScore_charge_v1() { return newCust_tradeScore_charge_v1; }
    public void setNewCust_tradeScore_charge_v1(String v) { this.newCust_tradeScore_charge_v1 = v; }
    public String getNewCust_v1_1_1_result() { return newCust_v1_1_1_result; }
    public void setNewCust_v1_1_1_result(String v) { this.newCust_v1_1_1_result = v; }
    public String getRule_score() { return rule_score; }
    public void setRule_score(String v) { this.rule_score = v; }
    public String getAdjust_coeff() { return adjust_coeff; }
    public void setAdjust_coeff(String v) { this.adjust_coeff = v; }
    public String getCreditLimit_cap() { return creditLimit_cap; }
    public void setCreditLimit_cap(String v) { this.creditLimit_cap = v; }
    public String getAddCredit_cap() { return addCredit_cap; }
    public void setAddCredit_cap(String v) { this.addCredit_cap = v; }
    public String getNewCust_mainModel_charge_v2() { return newCust_mainModel_charge_v2; }
    public void setNewCust_mainModel_charge_v2(String v) { this.newCust_mainModel_charge_v2 = v; }
    public String getNewCust_ruleScore_charge_v2() { return newCust_ruleScore_charge_v2; }
    public void setNewCust_ruleScore_charge_v2(String v) { this.newCust_ruleScore_charge_v2 = v; }
    public String getNewCust_creditLevel_charge_v2() { return newCust_creditLevel_charge_v2; }
    public void setNewCust_creditLevel_charge_v2(String v) { this.newCust_creditLevel_charge_v2 = v; }
    public String getNewCust_income_subScore_v1_2() { return newCust_income_subScore_v1_2; }
    public void setNewCust_income_subScore_v1_2(String v) { this.newCust_income_subScore_v1_2 = v; }
    public String getNewCust_debt_subScore_v1_2() { return newCust_debt_subScore_v1_2; }
    public void setNewCust_debt_subScore_v1_2(String v) { this.newCust_debt_subScore_v1_2 = v; }
    public String getNewCsut_lrScoreCard_0105_score() { return newCsut_lrScoreCard_0105_score; }
    public void setNewCsut_lrScoreCard_0105_score(String v) { this.newCsut_lrScoreCard_0105_score = v; }
    public String getNewCsut_lrScoreCard_0105_level() { return newCsut_lrScoreCard_0105_level; }
    public void setNewCsut_lrScoreCard_0105_level(String v) { this.newCsut_lrScoreCard_0105_level = v; }
    public String getNewCsut_lrScoreCard_0105_credit() { return newCsut_lrScoreCard_0105_credit; }
    public void setNewCsut_lrScoreCard_0105_credit(String v) { this.newCsut_lrScoreCard_0105_credit = v; }
    public String getNewWithdraw_lrScoreCard_0106_score() { return newWithdraw_lrScoreCard_0106_score; }
    public void setNewWithdraw_lrScoreCard_0106_score(String v) { this.newWithdraw_lrScoreCard_0106_score = v; }
    public String getNewWithdraw_lrScoreCard_0106_level() { return newWithdraw_lrScoreCard_0106_level; }
    public void setNewWithdraw_lrScoreCard_0106_level(String v) { this.newWithdraw_lrScoreCard_0106_level = v; }
    public String getNew_score1() { return new_score1; }
    public void setNew_score1(String v) { this.new_score1 = v; }
    public String getNew_score2() { return new_score2; }
    public void setNew_score2(String v) { this.new_score2 = v; }
    public String getNew_score3() { return new_score3; }
    public void setNew_score3(String v) { this.new_score3 = v; }
    public String getNew_level1() { return new_level1; }
    public void setNew_level1(String v) { this.new_level1 = v; }
    public String getNew_level2() { return new_level2; }
    public void setNew_level2(String v) { this.new_level2 = v; }
    public String getNew_level3() { return new_level3; }
    public void setNew_level3(String v) { this.new_level3 = v; }
    public String getOld_score1() { return old_score1; }
    public void setOld_score1(String v) { this.old_score1 = v; }
    public String getOld_score2() { return old_score2; }
    public void setOld_score2(String v) { this.old_score2 = v; }
    public String getOld_score3() { return old_score3; }
    public void setOld_score3(String v) { this.old_score3 = v; }
    public String getOld_level1() { return old_level1; }
    public void setOld_level1(String v) { this.old_level1 = v; }
    public String getOld_level2() { return old_level2; }
    public void setOld_level2(String v) { this.old_level2 = v; }
    public String getOld_level3() { return old_level3; }
    public void setOld_level3(String v) { this.old_level3 = v; }
}
