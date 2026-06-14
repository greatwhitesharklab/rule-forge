/**
 * FieldLabel — a small gray inline label used as a non-deprecated replacement
 * for the antd 5 `Input.addonBefore` prop.
 *
 * In antd 6 `addonBefore` / `addonAfter` are runtime-deprecated (console
 * warning: "use Space.Compact instead"). The React editor previously leaned
 * on `addonBefore` for the small Chinese field hints next to each input
 * (变量分类 / 变量名 / 标签 / 类型 / …). This helper reproduces that visual
 * — a compact gray text sitting at the left of the input — via the supported,
 * non-deprecated `Input.prefix` slot instead of `addonBefore`.
 *
 * Usage:
 *   <Input size="small" prefix={<FieldLabel>变量名</FieldLabel>} ... />
 *
 * The label is given a muted color and a right margin so it visually separates
 * from the typed text, matching the old addon look closely enough that the BA
 * workflow does not change.
 */
import type { ReactNode } from 'react';

export interface FieldLabelProps {
  children: ReactNode;
}

/**
 * Inline muted label rendered inside an Input prefix slot.
 * Kept as a component (not just a span) so call sites read clearly and the
 * styling lives in one place.
 */
export function FieldLabel({ children }: FieldLabelProps) {
  return (
    <span
      style={{
        color: '#888',
        marginRight: 6,
        whiteSpace: 'nowrap',
        userSelect: 'none',
      }}
    >
      {children}
    </span>
  );
}

export default FieldLabel;
