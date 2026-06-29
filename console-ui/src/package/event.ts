/**
 * V7.7.2:package/event stub — 老 .rp 知识包事件总线已废弃。
 * datasource/index.tsx 仍 emit OPEN_BATCH_TEST_DIALOG(老 .rp 批测入口)。
 * V1 决策流不走批测,事件 listener 全部失效。保留 stub 以维持编译。
 */
import {EventEmitter} from 'events';

export const OPEN_BATCH_TEST_DIALOG = 'OPEN_BATCH_TEST_DIALOG';
export const eventEmitter = new EventEmitter();
