/**
 * Barrel export for the React complex scorecard editor.
 *
 * Import surface:
 *   import { ComplexScoreCardEditor } from '@/editor/complexscorecard/react';
 *
 * The pure model (types / serialize / parse) lives in ../model and is
 * re-exported here for convenience. The shared model pieces
 * (AssignTarget / CardJoint / CardCondition / CardProperty / LibraryImport /
 * ScoringType) are re-exported via ../model/types from the plain scorecard
 * model.
 */
export { default as ComplexScoreCardEditor } from './ComplexScoreCardEditor';
export type { ComplexScoreCardEditorProps } from './ComplexScoreCardEditor';

export { default as EditorRoute } from './EditorRoute';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeComplexScoreCard } from '../model/serialize';
export { parseComplexScoreCard } from '../model/parse';
