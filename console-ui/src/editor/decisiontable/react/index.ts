/**
 * Barrel export for the React decision-table editor.
 *
 * Import surface:
 *   import { DecisionTableEditor } from '@/editor/decisiontable/react';
 *
 * The pure model (types / serialize / parse) lives in ../model and is
 * re-exported here for convenience.
 */
export { default as DecisionTableEditor } from './DecisionTableEditor';
export type { DecisionTableEditorProps } from './DecisionTableEditor';

export { default as CellEditor } from './CellEditor';
export type { CellEditorProps } from './CellEditor';

export { default as ColumnEditor } from './ColumnEditor';
export type { ColumnDraft, ColumnEditorProps } from './ColumnEditor';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeDecisionTable } from '../model/serialize';
export { parseDecisionTable } from '../model/parse';
