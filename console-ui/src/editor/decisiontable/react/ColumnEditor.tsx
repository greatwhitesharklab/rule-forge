/**
 * ColumnEditor — modal for adding / editing a decision-table column.
 *
 * Fields: type (Criteria / Assignment / ConsolePrint / ExecuteMethod), width,
 * and for Criteria columns the variable binding (category / name / label /
 * datatype). Matches the `<col>` attributes serialize.ts writes.
 *
 * Pure controlled form: parent owns the draft, applies it on OK.
 */
import { Form, Input, InputNumber, Modal, Select } from 'antd';
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
}

const TYPE_OPTIONS: { value: ColumnType; label: string }[] = [
  { value: 'Criteria', label: '条件列 (Criteria)' },
  { value: 'Assignment', label: '赋值列 (Assignment)' },
  { value: 'ConsolePrint', label: '打印列 (ConsolePrint)' },
  { value: 'ExecuteMethod', label: '执行方法列 (ExecuteMethod)' },
];

export function ColumnEditor({ open, column, onOk, onCancel }: ColumnEditorProps) {
  const [form] = Form.useForm<ColumnDraft>();
  const isCriteria = Form.useWatch('type', form) === 'Criteria';

  return (
    <Modal
      title={column ? '编辑列' : '添加列'}
      open={open}
      onCancel={onCancel}
      onOk={() => {
        form.validateFields().then((values) => onOk(values));
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
          <>
            <Form.Item name="variableCategory" label="变量分类 (var-category)">
              <Input placeholder="如 客户.客户 或 parameter" />
            </Form.Item>
            <Form.Item name="variableName" label="变量名 (var)">
              <Input placeholder="如 age" />
            </Form.Item>
            <Form.Item name="variableLabel" label="变量标签 (var-label)">
              <Input placeholder="如 年龄" />
            </Form.Item>
            <Form.Item name="datatype" label="数据类型 (datatype)">
              <Input placeholder="如 Integer" />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
}

export default ColumnEditor;
