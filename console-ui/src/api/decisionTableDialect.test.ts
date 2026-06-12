import { describe, it, expect } from 'vitest';
import {
    DEFAULT_DIALECT,
    detectDialectFromFilePath,
    dialectLabel,
    type TableDialect,
} from './decisionTableDialect';

/**
 * V5.40.6 — DecisionTable dialect 工具函数 Vitest BDD。
 *
 * <p>3 BDD 分 3 组:默认值 / 路径检测 / label 显示。
 */
describe('decisionTableDialect V5.40.6 BDD', () => {

    describe('Group 1 — DEFAULT_DIALECT', () => {
        it('Given DEFAULT_DIALECT 常量,When 读,Then 是 RULEFORGE_NATIVE(V5.39 老用法兼容)', () => {
            expect(DEFAULT_DIALECT).toBe('RULEFORGE_NATIVE');
        });
    });

    describe('Group 2 — detectDialectFromFilePath', () => {
        it('Given .dmn 路径,When detectDialectFromFilePath,Then 返回 DMN', () => {
            expect(detectDialectFromFilePath('rules/customer-tier.dmn')).toBe('DMN');
        });

        it('Given .dmn 路径 + 大写扩展名,When detectDialectFromFilePath,Then 仍返回 DMN', () => {
            expect(detectDialectFromFilePath('rules/Customer-Tier.DMN')).toBe('DMN');
        });

        it('Given .xml 路径,When detectDialectFromFilePath,Then 返回 RULEFORGE_NATIVE(老用法)', () => {
            expect(detectDialectFromFilePath('rules/legacy-table.xml')).toBe<TableDialect>('RULEFORGE_NATIVE');
        });

        it('Given 空路径,When detectDialectFromFilePath,Then 返回 DEFAULT_DIALECT(RULEFORGE_NATIVE)', () => {
            expect(detectDialectFromFilePath('')).toBe(DEFAULT_DIALECT);
        });

        it('Given null/undefined 路径,When detectDialectFromFilePath,Then 返回 DEFAULT_DIALECT', () => {
            expect(detectDialectFromFilePath(null as unknown as string)).toBe(DEFAULT_DIALECT);
            expect(detectDialectFromFilePath(undefined as unknown as string)).toBe(DEFAULT_DIALECT);
        });
    });

    describe('Group 3 — dialectLabel(给 "Source format" 指示器用)', () => {
        it('Given DMN dialect,When dialectLabel,Then 返回 "DMN 1.3 (V5.40+)"', () => {
            expect(dialectLabel('DMN')).toBe('DMN 1.3 (V5.40+)');
        });

        it('Given RULEFORGE_NATIVE dialect,When dialectLabel,Then 返回 "RuleForge XML (legacy)"', () => {
            expect(dialectLabel('RULEFORGE_NATIVE')).toBe('RuleForge XML (legacy)');
        });

        it('Given null dialect(V5.39 老 .xml 反序列化产物),When dialectLabel,Then 返回 "RuleForge XML (legacy)"', () => {
            expect(dialectLabel(null)).toBe('RuleForge XML (legacy)');
        });

        it('Given undefined dialect,When dialectLabel,Then 返回 "RuleForge XML (legacy)"', () => {
            expect(dialectLabel(undefined)).toBe('RuleForge XML (legacy)');
        });
    });
});
