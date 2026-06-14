/**
 * Barrel export for the React crosstab editor.
 *
 * Import surface:
 *   import { CrossTableEditor } from '@/editor/crosstab/react';
 *
 * The pure model (types / serialize / parse) lives in ../model and is
 * re-exported here for convenience.
 */
export { default as CrossTableEditor } from './CrossTableEditor';
export type { CrossTableEditorProps } from './CrossTableEditor';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeCrossTable } from '../model/serialize';
export { parseCrossTable } from '../model/parse';
