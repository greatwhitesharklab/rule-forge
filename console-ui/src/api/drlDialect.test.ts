import {describe, expect, test} from 'vitest';
import {
    DEFAULT_DIALECT,
    detectDrlDialectFromFilePath,
    drlDialectBadgeColor,
    drlDialectLabel,
    type DrlDialect
} from './drlDialect.js';

describe('V5.42.7 / V5.43 — drlDialect utility', () => {
    describe('detectDrlDialectFromFilePath', () => {
        test('空 / undefined → DEFAULT_DIALECT', () => {
            expect(detectDrlDialectFromFilePath('')).toBe(DEFAULT_DIALECT);
            expect(detectDrlDialectFromFilePath(undefined as unknown as string)).toBe(DEFAULT_DIALECT);
        });

        test('.drl 后缀 → DRL', () => {
            expect(detectDrlDialectFromFilePath('rules/main.drl')).toBe('DRL');
        });

        test('.dslrd 后缀 → DRL(Drools 6 rule descriptor)', () => {
            expect(detectDrlDialectFromFilePath('rules/loan.dslrd')).toBe('DRL');
        });

        test('.dslr 后缀 → DRL(新版 Drools 6 rule descriptor,V5.43 收口后允许)', () => {
            expect(detectDrlDialectFromFilePath('rules/loan.dslr')).toBe('DRL');
        });

        test('V5.43+.xml 后缀 → DEFAULT(不再 → RULEFORGE_NATIVE,legacy-free 收口)', () => {
            expect(detectDrlDialectFromFilePath('rules/legacy.xml')).toBe(DEFAULT_DIALECT);
        });

        test('V5.43+.ul 后缀 → DEFAULT(不再 → RULEFORGE_NATIVE,legacy-free 收口)', () => {
            expect(detectDrlDialectFromFilePath('rules/zh.ul')).toBe(DEFAULT_DIALECT);
        });

        test('大写后缀也识别', () => {
            expect(detectDrlDialectFromFilePath('rules/MAIN.DRL')).toBe('DRL');
        });
    });

    describe('drlDialectLabel', () => {
        test('null → V5.43+ legacy-free label', () => {
            expect(drlDialectLabel(null)).toContain('V5.43');
        });

        test('RULEFORGE_NATIVE → V5.43 deprecated label', () => {
            expect(drlDialectLabel('RULEFORGE_NATIVE')).toContain('V5.43 deprecated');
        });

        test('DRL → V5.43+ legacy-free label', () => {
            expect(drlDialectLabel('DRL')).toContain('V5.43+ legacy-free');
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
        test('V5.43+ 改 DRL(路线 B 收口后 production 仅支持 DRL 4)', () => {
            expect(DEFAULT_DIALECT).toBe<DrlDialect>('DRL');
        });
    });
});
