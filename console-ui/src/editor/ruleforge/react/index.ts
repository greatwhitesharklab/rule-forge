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

export { default as RulePropertyEditor } from './RulePropertyEditor';
export type { RulePropertyEditorProps } from './RulePropertyEditor';

// Pure helpers (the unit-testable core).
export * from './flowLayout';
export * from './constants';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeRuleset } from '../model/serialize';
export { parseRuleset } from '../model/parse';
