/**
 * RulePropertyEditor — controlled editor for a Rule's `properties` list.
 *
 * A rule may carry any subset of the 10 supported `<rule>` attributes
 * (salience / loop / effective-date / expires-date / enabled / debug /
 * activation-group / agenda-group / auto-focus / ruleflow-group). This
 * component shows the currently-set properties with the right editor widget
 * per property (text / date / boolean), and an "add property" dropdown that
 * only offers the not-yet-added properties.
 *
 * Data flow is single-direction; the parent Rule owns the list and replaces
 * it via onChange on every edit.
 */
import { Button, DatePicker, Input, Select, Space, Tag } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import type { RuleProperty } from '../model/types';
import { RULE_PROPERTY_DEFS, findPropertyDef, propertyLabel } from './constants';

export interface RulePropertyEditorProps {
  /** Current property list (controlled). */
  value: RuleProperty[];
  /** Called with a new list on every edit. */
  onChange: (next: RuleProperty[]) => void;
}

export function RulePropertyEditor({ value, onChange }: RulePropertyEditorProps) {
  const setProperty = (name: string, next: string | boolean) => {
    const idx = value.findIndex((p) => p.name === name);
    if (idx >= 0) {
      const copy = value.slice();
      copy[idx] = { name, value: next };
      onChange(copy);
    }
  };

  const removeProperty = (name: string) => {
    onChange(value.filter((p) => p.name !== name));
  };

  const addProperty = (name: string) => {
    const def = findPropertyDef(name);
    if (!def) return;
    if (value.some((p) => p.name === name)) return;
    onChange(value.concat([{ name, value: def.defaultValue }]));
  };

  const available = RULE_PROPERTY_DEFS.filter((d) => !value.some((p) => p.name === d.name));

  return (
    <div>
      <Space wrap size={[8, 4]}>
        {value.map((p) => (
          <PropertyRow
            key={p.name}
            prop={p}
            onValueChange={(v) => setProperty(p.name, v)}
            onRemove={() => removeProperty(p.name)}
          />
        ))}
      </Space>
      {available.length > 0 && (
        <div style={{ marginTop: 4 }}>
          <Select
            size="small"
            style={{ width: 160 }}
            placeholder="+ 添加属性"
            value={undefined}
            onChange={addProperty}
            options={available.map((d) => ({ value: d.name, label: d.label }))}
          />
        </div>
      )}
    </div>
  );
}

/** A single property row — label + value widget + delete. */
function PropertyRow({
  prop,
  onValueChange,
  onRemove,
}: {
  prop: RuleProperty;
  onValueChange: (v: string | boolean) => void;
  onRemove: () => void;
}) {
  const def = findPropertyDef(prop.name);
  const label = propertyLabel(prop.name);

  return (
    <Space size={4} style={{ alignItems: 'center' }}>
      <Tag color="geekblue" style={{ margin: 0 }}>
        {label}
      </Tag>
      {def?.editorType === 'boolean' ? (
        <Select
          size="small"
          style={{ width: 70 }}
          value={prop.value === true ? 'true' : 'false'}
          onChange={(v) => onValueChange(v === 'true')}
          options={[
            { value: 'true', label: '是' },
            { value: 'false', label: '否' },
          ]}
        />
      ) : def?.editorType === 'date' ? (
        <DatePicker
          size="small"
          showTime
          style={{ width: 180 }}
          value={typeof prop.value === 'string' && prop.value ? safeDayjs(prop.value) : undefined}
          onChange={(_d: Dayjs | null, str: string) => onValueChange(str)}
        />
      ) : (
        <Input
          size="small"
          style={{ width: 120 }}
          value={typeof prop.value === 'string' ? prop.value : ''}
          onChange={(e) => onValueChange(e.target.value)}
        />
      )}
      <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={onRemove} />
    </Space>
  );
}

/**
 * Convert a saved date string back to a Dayjs the DatePicker can show. Dayjs
 * is a transitive dependency of antd, so it's always resolvable.
 */
function safeDayjs(s: string): Dayjs {
  return dayjs(s);
}

export default RulePropertyEditor;
