import {describe, it, expect} from 'vitest';
import {rqbToCel, type RqbGroup} from './rqbToCel';

describe('W3-4 rqbToCel — React Query Builder → CEL', () => {
    it('单 = 规则 → field == value', () => {
        const q: RqbGroup = {combinator: 'and', rules: [{field: 'age', operator: '>=', value: 18}]};
        expect(rqbToCel(q)).toBe('age >= 18');
    });

    it('字符串值加引号', () => {
        const q: RqbGroup = {combinator: 'and', rules: [{field: 'name', operator: '=', value: 'alice'}]};
        expect(rqbToCel(q)).toBe("name == 'alice'");
    });

    it('布尔/数字/null 原样不加引号', () => {
        expect(rqbToCel({combinator: 'and', rules: [{field: 'b', operator: '=', value: true}]})).toBe('b == true');
        expect(rqbToCel({combinator: 'and', rules: [{field: 'n', operator: '=', value: null}]})).toBe('n == null');
    });

    it('and 组合 → &&', () => {
        const q: RqbGroup = {combinator: 'and', rules: [
            {field: 'score', operator: '>', value: 600},
            {field: 'blacklisted', operator: '=', value: false},
        ]};
        expect(rqbToCel(q)).toBe('score > 600 && blacklisted == false');
    });

    it('or 组合 → ||', () => {
        const q: RqbGroup = {combinator: 'or', rules: [
            {field: 'age', operator: '<', value: 25},
            {field: 'score', operator: '>', value: 800},
        ]};
        expect(rqbToCel(q)).toBe('age < 25 || score > 800');
    });

    it('嵌套组 → 括号', () => {
        const q: RqbGroup = {combinator: 'and', rules: [
            {field: 'a', operator: '=', value: 1},
            {combinator: 'or', rules: [
                {field: 'b', operator: '=', value: 2},
                {field: 'c', operator: '=', value: 3},
            ]},
        ]};
        expect(rqbToCel(q)).toBe('a == 1 && (b == 2 || c == 3)');
    });

    it('in/notIn → value in field', () => {
        expect(rqbToCel({combinator: 'and', rules: [{field: 'tags', operator: 'in', value: "'vip'"}]})).toBe("'vip' in tags");
    });

    it('空规则 → 空串', () => {
        expect(rqbToCel(null)).toBe('');
        expect(rqbToCel({combinator: 'and', rules: []})).toBe('');
    });
});
