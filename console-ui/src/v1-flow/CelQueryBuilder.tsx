import {useState, useEffect} from 'react';
import {QueryBuilder, type RuleGroupType, type Field} from 'react-querybuilder';
import 'react-querybuilder/dist/query-builder.css';
import {rqbToCel} from './rqbToCel';

/**
 * React Query Builder 可视化条件编辑器 → CEL(W3-4)。
 *
 * <p>运营不写 CEL,可视化拼字段/操作符/值 → 自动生成 CEL 字符串(进 RuleAsset)。
 * 一向:RQB → CEL(CEL 是 source of truth)。CEL→RQB 反向解析 MVP 不做
 * (切可视化模式若有复杂 CEL,RQB 从空开始)。
 *
 * <p>fields 由调用方传(schema 字段);缺省给现金贷常见字段让 demo 可用。
 */
const DEFAULT_FIELDS: Field[] = [
    {name: 'age', label: '年龄'},
    {name: 'income', label: '月收入'},
    {name: 'score', label: '征信分'},
    {name: 'blacklisted', label: '黑名单'},
    {name: 'riskScore', label: '风险分'},
    {name: 'decision', label: '决策'},
];

export default function CelQueryBuilder({
    cel,
    onChange,
    fields,
}: {
    cel: string;
    onChange: (cel: string) => void;
    fields?: Field[];
}) {
    const emptyQuery: RuleGroupType = {combinator: 'and', rules: []};
    const [query, setQuery] = useState<RuleGroupType>(emptyQuery);

    // 切到此组件时若 cel 已非空,RQB 保持空(不解析),不覆盖已有 CEL
    useEffect(() => {
        if (!cel) setQuery(emptyQuery);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [cel === '']);

    return (
        <QueryBuilder
            fields={fields || DEFAULT_FIELDS}
            query={query}
            onQueryChange={(q) => {
                setQuery(q as RuleGroupType);
                onChange(rqbToCel(q as unknown as Parameters<typeof rqbToCel>[0]));
            }}
            controlClassnames={{queryBuilder: 'v1-rqb'}}
            controlElements={undefined}
            translations={{addRule: {label: '+ 条件'}, addGroup: {label: '+ 组', title: '添加条件组'}}}
        />
    );
}
