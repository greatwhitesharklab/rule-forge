import {describe, expect, test} from 'vitest';
import {
    DEFAULT_DIALECT,
    detectDrlDialectFromFilePath,
    drlDialectBadgeColor,
    drlDialectLabel,
    type DrlDialect
} from './drlDialect.js';

describe('V5.42.7 — drlDialect utility', () => {
    describe('detectDrlDialectFromFilePath', () => {
        test('空 / undefined → DEFAULT_DIALECT', () => {
            expect(detectDrlDialectFromFilePath('')).toBe(DEFAULT_DIALECT);
            expect(detectDrlDialectFromFilePath(undefined as unknown as string)).toBe(DEFAULT_DIALECT);
        });

        test('.drl 后缀 → DRL', () => {
            expect(detectDrlDialectFromFilePath('rules/main.drl')).toBe('DRL');
        });

        test('.dsl 后缀 → DRL(Drools 6 mapping 文件)', () => {
            expect(detectDrlDialectFromFilePath('mappings/loan.dsl')).toBe('DRL');
        });

        test('.dslrd 后缀 → DRL(Drools 6 rule descriptor)', () => {
            expect(detectDrlDialectFromFilePath('rules/loan.dslrd')).toBe('DRL');
        });

        test('.xml 后缀 → RULEFORGE_NATIVE(legacy)', () => {
            expect(detectDrlDialectFromFilePath('rules/legacy.xml')).toBe(DEFAULT_DIALECT);
        });

        test('.ul 后缀 → RULEFORGE_NATIVE(legacy,中文改写版)', () => {
            expect(detectDrlDialectFromFilePath('rules/zh.ul')).toBe(DEFAULT_DIALECT);
        });

        test('大写后缀也识别', () => {
            expect(detectDrlDialectFromFilePath('rules/MAIN.DRL')).toBe('DRL');
        });
    });

    describe('drlDialectLabel', () => {
        test('null → legacy label', () => {
            expect(drlDialectLabel(null)).toContain('legacy');
        });

        test('RULEFORGE_NATIVE → legacy label', () => {
            expect(drlDialectLabel('RULEFORGE_NATIVE')).toContain('RuleForge');
        });

        test('DRL → V5.42+ label', () => {
            expect(drlDialectLabel('DRL')).toContain('V5.42');
        });
    });

    describe('drlDialectBadgeColor', () => {
        test('DRL → geekblue(主推)', () => {
            expect(drlDialectBadgeColor('DRL')).toBe('geekblue');
        });

        test('RULEFORGE_NATIVE / null → default(灰)', () => {
            expect(drlDialectBadgeColor('RULEFORGE_NATIVE')).toBe('default');
            expect(drlDialectBadgeColor(null)).toBe('default');
        });
    });

    describe('DEFAULT_DIALECT 常量', () => {
        test('值是 RULEFORGE_NATIVE(跟 V5.40.6 / V5.41.6 一致)', () => {
            expect(DEFAULT_DIALECT).toBe<DrlDialect>('RULEFORGE_NATIVE');
        });
    });
});
