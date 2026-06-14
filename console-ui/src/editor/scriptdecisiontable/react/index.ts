/**
 * Barrel export for the React script-decision-table editor.
 *
 * Import surface:
 *   import { ScriptDecisionTableEditor } from '@/editor/scriptdecisiontable/react';
 *
 * The pure model (types / serialize / parse) lives in ../model and is
 * re-exported here for convenience.
 */
export { default as ScriptDecisionTableEditor } from './ScriptDecisionTableEditor';
export type { ScriptDecisionTableEditorProps } from './ScriptDecisionTableEditor';

export { default as ScriptCellEditor } from './ScriptCellEditor';
export type { ScriptCellEditorProps } from './ScriptCellEditor';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeScriptDecisionTable } from '../model/serialize';
export { parseScriptDecisionTable } from '../model/parse';
