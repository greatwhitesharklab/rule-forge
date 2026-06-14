/**
 * ColumnEditor — modal for adding / editing a decision-table column.
 *
 * Fields: type (Criteria / Assignment / ConsolePrint / ExecuteMethod), width,
 * and for Criteria columns the variable binding (category / name / label /
 * datatype). Matches the `<col>` attributes serialize.ts writes.
 *
 * For the variable binding the Criteria column uses the shared
 * {@link VariablePicker} (a two-level category→variable Cascader) backed by
 * the project's imported variable libraries; the libraries are fetched via
 * the `useVariableLibraries` hook from the `.vl.xml` paths declared on the
 * decision table (`<import-variable-library path="…"/>`). Picking a variable
 * fills all four binding fields at once, replacing the previous four free-text
 * inputs. The `Form` keeps controlled fields so the draft still applies on OK.
 *
 * Pure controlled form: parent owns the draft, applies it on OK.
 */
import { Form, Input, InputNumber, Modal, Select } from 'antd';
import { useEffect, useState } from 'react';
import { VariablePicker, useVariableLibraries } from '../../ruleforge/react';
import type { VariableBinding } from '../../ruleforge/react';
import type { Column, ColumnType } from '../model/types';

export interface ColumnDraft {
  type: ColumnType;
  width: number;
  variableCategory?: string;
  variableName?: string;
  variableLabel?: string;
  datatype?: string;
}

export interface ColumnEditorProps {
  open: boolean;
  /** Existing column to edit, or undefined when adding. */
  column?: Column;
  /** Called with the draft when the user clicks OK. */
  onOk: (draft: ColumnDraft) => void;
  /** Called when the user cancels. */
  onCancel: () => void;
  /**
   * The `.vl.xml` paths the decision table imports (the
   * `<import-variable-library path="…"/>` entries). Used by the embedded
   * VariablePicker to load the project's variable categories. Optional —
   * when omitted the picker shows "未加载变量库" and the binding fields fall
   * back to free-text inputs.
   */
  variableLibraryPaths?: string[];
}

const TYPE_OPTIONS: { value: ColumnType; label: string }[] = [
  { value: 'Criteria', label: '条件列 (Criteria)' },
  { value: 'Assignment', label: '赋值列 (Assignment)' },
  { value: 'ConsolePrint', label: '打印列 (ConsolePrint)' },
  { value: 'ExecuteMethod', label: '执行方法列 (ExecuteMethod)' },
];

export function ColumnEditor({ open, column, onOk, onCancel, variableLibraryPaths = [] }: ColumnEditorProps) {
  const [form] = Form.useForm<ColumnDraft>();
  const isCriteria = Form.useWatch('type', form) === 'Criteria';

  // Hold the variable binding in a local state separate from the Form so the
  // VariablePicker (which emits a single composite binding object) does not
  // have to fight the Form's per-field model. Seeded from the column on open.
  const [binding, setBinding] = useState<VariableBinding>({
    varCategory: column?.variableCategory ?? '',
    var: column?.variableName ?? '',
    varLabel: column?.variableLabel ?? '',
    datatype: column?.datatype ?? '',
  });

  // Re-seed the binding whenever the modal opens for a different column.
  useEffect(() => {
    if (open) {
      setBinding({
        varCategory: column?.variableCategory ?? '',
        var: column?.variableName ?? '',
        varLabel: column?.variableLabel ?? '',
        datatype: column?.datatype ?? '',
      });
    }
  }, [open, column]);

  // Load the variable libraries once (paths are stable per table). The hook
  // is silent and degrades to an empty array on failure — the picker then
  // renders its "未加载变量库" placeholder.
  const { libraries } = useVariableLibraries(variableLibraryPaths);
  const hasLibraries = libraries.some((lib) => (lib || []).length > 0);

  return (
    <Modal
      title={column ? '编辑列' : '添加列'}
      open={open}
      onCancel={onCancel}
      onOk={() => {
        form.validateFields().then((values) =>
          onOk({
            ...values,
            // Spread the picker binding over the form values so the picker
            // wins when both were touched.
            ...(isCriteria
              ? {
                  variableCategory: binding.varCategory ?? '',
                  variableName: binding.var ?? '',
                  variableLabel: binding.varLabel ?? '',
                  datatype: binding.datatype ?? '',
                }
              : {}),
          }),
        );
      }}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={
          column
            ? {
                type: column.type,
                width: column.width,
                variableCategory: column.variableCategory,
                variableName: column.variableName,
                variableLabel: column.variableLabel,
                datatype: column.datatype,
              }
            : { type: 'Criteria', width: 160 }
        }
      >
        <Form.Item name="type" label="列类型" rules={[{ required: true }]}>
          <Select options={TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item name="width" label="列宽">
          <InputNumber min={60} max={600} />
        </Form.Item>
        {isCriteria && (
          <Form.Item label="变量绑定 (var-category / var / var-label / datatype)">
            {hasLibraries ? (
              <VariablePicker
                value={binding}
                onChange={setBinding}
                libraries={libraries}
                allowDatatypeEdit
              />
            ) : (
              // Fallback when no libraries are loaded: keep the original
              // free-text inputs so the column can still be bound by hand.
              <>
                <Form.Item name="variableCategory" label="变量分类 (var-category)" style={{ marginBottom: 8 }}>
                  <Input placeholder="如 客户.客户 或 parameter" />
                </Form.Item>
                <Form.Item name="variableName" label="变量名 (var)" style={{ marginBottom: 8 }}>
                  <Input placeholder="如 age" />
                </Form.Item>
                <Form.Item name="variableLabel" label="变量标签 (var-label)" style={{ marginBottom: 8 }}>
                  <Input placeholder="如 年龄" />
                </Form.Item>
                <Form.Item name="datatype" label="数据类型 (datatype)" style={{ marginBottom: 0 }}>
                  <Input placeholder="如 Integer" />
                </Form.Item>
              </>
            )}
          </Form.Item>
        )}
      </Form>
    </Modal>
  );
}

export default ColumnEditor;
