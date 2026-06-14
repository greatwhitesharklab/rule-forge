/**
 * Barrel export for the React scorecard editor.
 *
 * Import surface:
 *   import { ScoreCardEditor } from '@/editor/scorecard/react';
 *
 * The pure model (types / serialize / parse) lives in ../model and is
 * re-exported here for convenience.
 */
export { default as ScoreCardEditor } from './ScoreCardEditor';
export type { ScoreCardEditorProps } from './ScoreCardEditor';

export { default as EditorRoute } from './EditorRoute';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeScoreCard } from '../model/serialize';
export { parseScoreCard } from '../model/parse';
