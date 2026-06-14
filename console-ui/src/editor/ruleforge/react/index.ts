/**
 * Barrel export for the React ruleforge editor.
 *
 * Import surface:
 *   import { RulesetEditor } from '@/editor/ruleforge/react';
 * or for sub-components:
 *   import { ConditionFlow, AtomEditor } from '@/editor/ruleforge/react';
 *
 * The pure model (types / serialize / parse) lives in ../model and is
 * re-exported here for convenience.
 */
export { default as RulesetEditor } from './RulesetEditor';
export type { RulesetEditorProps } from './RulesetEditor';

export { default as RuleEditor } from './RuleEditor';
export type { RuleEditorProps } from './RuleEditor';

export { default as ConditionFlow } from './ConditionFlow';
export type { ConditionFlowProps } from './ConditionFlow';

export { default as AtomEditor } from './AtomEditor';
export type { AtomEditorProps } from './AtomEditor';

export { default as ActionEditor } from './ActionEditor';
export type { ActionEditorProps } from './ActionEditor';

export { default as ValueEditor } from './ValueEditor';
export type { ValueEditorProps } from './ValueEditor';

export { default as LeftValueEditor } from './LeftValueEditor';
export type { LeftValueEditorProps } from './LeftValueEditor';

// Shared variable-library browser (used by every React rule editor's
// Criteria / variable binding).
export { VariablePicker } from './VariablePicker';
export type {
  VariablePickerProps,
  VariableBinding,
  PickerVariableItem,
  PickerVariableCategory,
  VariableCategoryGroup,
} from './VariablePicker';
export { useVariableLibraries } from './useVariableLibraries';
export type { UseVariableLibrariesResult } from './useVariableLibraries';

// Shared constant-library browser (used by ValueEditor's Constant type).
export { ConstantPicker } from './ConstantPicker';
export type {
  ConstantPickerProps,
  ConstantBinding,
  PickerConstantItem,
  PickerConstantCategory,
  ConstantCategoryGroup,
} from './ConstantPicker';
export { useConstantLibraries } from './useConstantLibraries';
export type { UseConstantLibrariesResult } from './useConstantLibraries';

// Shared parameter-library browser (used by ValueEditor's Parameter type).
export { ParameterPicker } from './ParameterPicker';
export type {
  ParameterPickerProps,
  ParameterBinding,
  PickerParameterItem,
  ParameterLibrary,
} from './ParameterPicker';
export { useParameterLibraries } from './useParameterLibraries';
export type { UseParameterLibrariesResult } from './useParameterLibraries';

// Shared action-library browser (used by ActionEditor's execute-method kind).
export { MethodPicker } from './MethodPicker';
export type {
  MethodPickerProps,
  MethodBinding,
  PickerSpringBean,
  PickerActionMethod,
  PickerActionParameter,
  ActionLibrary,
} from './MethodPicker';
export { useActionLibraries } from './useActionLibraries';
export type { UseActionLibrariesResult } from './useActionLibraries';

export { default as RulePropertyEditor } from './RulePropertyEditor';
export type { RulePropertyEditorProps } from './RulePropertyEditor';

// Pure helpers (the unit-testable core).
export * from './flowLayout';
export * from './constants';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeRuleset } from '../model/serialize';
export { parseRuleset } from '../model/parse';
