import {useState} from 'react';
import {Segmented} from 'antd';
import CelEditor from './CelEditor';
import CelQueryBuilder from './CelQueryBuilder';
import type {Field} from 'react-querybuilder';

/**
 * 条件编辑器:Monaco(CEL 高级模式)↔ React Query Builder(可视化)切换(W3-4)。
 *
 * <p>CEL 是 source of truth — RQB 模式编辑生成 CEL,Monaco 模式直接写/看 CEL。
 * 运营用可视化,高级用户用 CEL。
 */
export default function ConditionEditor({
    value, onChange, fields, height,
}: {
    value: string;
    onChange: (v: string) => void;
    fields?: Field[];
    height?: number;
}) {
    const [mode, setMode] = useState<'rqb' | 'cel'>('rqb');
    return (
        <div>
            <Segmented
                size='small'
                value={mode}
                onChange={(m) => setMode(m as 'rqb' | 'cel')}
                options={[{value: 'rqb', label: '可视化'}, {value: 'cel', label: 'CEL'}]}
                style={{marginBottom: 6}}
            />
            {mode === 'rqb'
                ? <CelQueryBuilder cel={value} onChange={onChange} fields={fields}/>
                : <CelEditor value={value} onChange={onChange} height={height}/>}
        </div>
    );
}
