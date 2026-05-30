package com.ruleforge.console.app.service;

import com.ruleforge.console.app.entity.AlertHistory;
import com.ruleforge.console.app.entity.AlertRule;

import java.util.Date;
import java.util.List;

public interface IAlertService {
    void evaluateAlerts();
    List<AlertRule> listAlertRules();
    AlertRule saveAlertRule(AlertRule rule);
    AlertRule updateAlertRule(AlertRule rule);
    void deleteAlertRule(Long id);
    List<AlertHistory> listAlertHistory(Long alertRuleId, Date startTime, Date endTime);
}
