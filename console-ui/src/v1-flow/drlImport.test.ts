import { describe, it, expect } from 'vitest';
import {parseDrlToRuleSet} from './drlImport';

/** V7.18 — parseDrlToRuleSet BDD。覆盖支持的 DRL 子集各分支 + 降级 warning。 */

describe('V7.18 — parseDrlToRuleSet', () => {

    it('单条 rule,单条件 + setDecision → 1 V1 Rule(SET_DECISION)', () => {
        const drl = `rule "approveAdult" salience 100
when
    age >= 18
then
    setDecision("approve");
end`;
        const r = parseDrlToRuleSet(drl, 'LoanApplication');
        expect(r.warnings.filter((w) => !w.includes('modify')).join('|')).not.toMatch(/不支持/);
        expect(r.ruleSet.rules).toHaveLength(1);
        const rule = r.ruleSet.rules[0];
        expect(rule.name).toBe('approveAdult');
        expect(rule.priority).toBe(100);
        expect(rule.condition).toBe('age >= 18');
        expect(rule.actions).toEqual([{type: 'SET_DECISION', value: 'approve'}]);
    });

    it('多条件用 && / 逗号 → CEL && 连接', () => {
        const drl = `rule "x" when age >= 18 && blacklisted == false then setDecision("ok"); end`;
        const r = parseDrlToRuleSet(drl);
        expect(r.ruleSet.rules[0].condition).toBe('age >= 18 && blacklisted == false');
    });

    it('reject action → REJECT', () => {
        const drl = `rule "block" when blacklisted == true then reject("BLACKLIST"); end`;
        const r = parseDrlToRuleSet(drl);
        expect(r.ruleSet.rules[0].actions).toEqual([{type: 'REJECT', reason: 'BLACKLIST'}]);
    });

    it('set <field>(<value>) 顶层 → SET_VARIABLE', () => {
        const drl = `rule "setRate" when vip == true then set rate(0.12); end`;
        const r = parseDrlToRuleSet(drl);
        const acts = r.ruleSet.rules[0].actions;
        expect(acts).toEqual([{type: 'SET_VARIABLE', target: 'rate', value: '0.12'}]);
    });

    it('多条 rule → 多条 V1 Rule', () => {
        const drl = `rule "a" when age >= 18 then setDecision("ok"); end
rule "b" when age < 18 then setDecision("minor"); end`;
        const r = parseDrlToRuleSet(drl);
        expect(r.ruleSet.rules).toHaveLength(2);
        expect(r.ruleSet.rules.map((x: any) => x.name)).toEqual(['a', 'b']);
    });

    it('package / import / global → 忽略(rule 仍解析)', () => {
        const drl = `package com.x;
import com.x.Y;
global java.util.List list;
rule "r" when age >= 18 then setDecision("ok"); end`;
        const r = parseDrlToRuleSet(drl);
        expect(r.ruleSet.rules).toHaveLength(1);
        expect(r.ruleSet.rules[0].name).toBe('r');
    });

    it('条件含 || → warning 降级(本适配器只支持 AND)', () => {
        const drl = `rule "x" when age < 18 || age >= 65 then setDecision("special"); end`;
        const r = parseDrlToRuleSet(drl);
        expect(r.warnings.some((w) => /rule\[1\].*\|\|/.test(w))).toBe(true);
    });

    it('modify($fact){...} 块 → warning(set 仍尽力提取)', () => {
        const drl = `rule "x" when age >= 18 then modify($f){ set approved(true) }; end`;
        const r = parseDrlToRuleSet(drl);
        expect(r.warnings.some((w) => /modify/.test(w))).toBe(true);
    });

    it('无法解析的 rule → warning + 跳过(不中断其他)', () => {
        const drl = `rule "good" when age >= 18 then setDecision("ok"); end
rule "bad" when impossible gibberish then ; end`;
        const r = parseDrlToRuleSet(drl);
        // good rule 解析成功, bad rule 跳过
        expect(r.ruleSet.rules.length).toBeGreaterThanOrEqual(1);
        expect(r.ruleSet.rules[0].name).toBe('good');
    });

    it('空 DRL → warning,无 rule', () => {
        const r = parseDrlToRuleSet('');
        expect(r.ruleSet.rules).toHaveLength(0);
        expect(r.warnings.some((w) => /未解析到/.test(w))).toBe(true);
    });
});