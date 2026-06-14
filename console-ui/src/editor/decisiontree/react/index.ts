/**
 * Barrel export for the React decisiontree editor.
 *
 * Import surface:
 *   import { DecisionTreeApp } from '@/editor/decisiontree/react';
 * or for sub-components:
 *   import { DecisionTreeFlow } from '@/editor/decisiontree/react';
 *
 * The pure model (types / serialize / parse) lives in ../model and is
 * re-exported here for convenience.
 */
export { default as DecisionTreeApp } from './DecisionTreeApp';
export type { DecisionTreeAppProps } from './DecisionTreeApp';

export { DecisionTreeFlow } from './DecisionTreeEditor';
export type { DecisionTreeFlowProps } from './DecisionTreeEditor';

// Pure helpers (the unit-testable core).
export * from './flowLayout';

// Re-export the pure model so a single import line reaches everything.
export * from '../model/types';
export { serializeDecisionTree } from '../model/serialize';
export { parseDecisionTree } from '../model/parse';
