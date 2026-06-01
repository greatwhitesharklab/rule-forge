/**
 * Core editor utilities: container generation, library refresh, global arrays.
 *
 * Previously used `window.ruleforge = {}` as a namespace for prototype-based classes.
 * Now exports module-level arrays and functions.  Side-effects still set window globals
 * for backward compatibility during the migration period.
 */

import { formPost, httpGet } from '../../api/client.js';

/** Helper to create an inline-editable container element. */
export function generateContainer(): HTMLElement {
    const container = document.createElement('span');
    container.textContent = '.';
    container.style.cssText = 'height:20px;cursor:pointer;margin:0px;color:white;border:dashed transparent 1px;';
    container.addEventListener('mouseover', () => {
        container.style.border = 'dashed gray 1px';
    });
    container.addEventListener('mouseout', () => {
        container.style.border = 'dashed transparent 1px';
    });
    return container;
}

// --- Library arrays ---

export const constantLibraries: string[] = [];
export const actionLibraries: string[] = [];
export const variableLibraries: string[] = [];
export const parameterLibraries: string[] = [];

// --- Widget registration arrays (populated by each widget constructor) ---

export const constantValueArray: Array<{ initMenu(data: unknown[]): void }> = [];
export const actionTypeArray: Array<{ initMenu(data: unknown[]): void }> = [];
export const variableValueArray: Array<{ initMenu(data: unknown[]): void }> = [];
export const parameterValueArray: Array<{ initMenu(data: unknown[]): void }> = [];
export const functionValueArray: Array<{ initMenu(data: unknown[]): void }> = [];

// --- Library refresh functions ---

function joinLibraryFiles(libs: string[]): string {
    return libs.join(';');
}

export function refreshParameterLibraries(): void {
    const parameterFiles = joinLibraryFiles(parameterLibraries);
    if (parameterFiles === '' || parameterFiles.length < 2) {
        return;
    }
    formPost<unknown[]>('/common/loadXml', { files: parameterFiles })
        .then((data: unknown[]) => {
            window._ruleforgeEditorParameterLibraries = data;
            parameterValueArray.forEach(item => {
                item.initMenu(data);
            });
        })
        .catch(() => {
            window.bootbox.alert('加载文件失败！');
        });
}

export function refreshVariableLibraries(): void {
    const variableFiles = joinLibraryFiles(variableLibraries);
    if (variableFiles === '' || variableFiles.length < 2) {
        return;
    }
    formPost<unknown[]>('/common/loadXml', { files: variableFiles })
        .then((data: unknown[]) => {
            window._ruleforgeEditorVariableLibraries = data;
            variableValueArray.forEach(item => {
                item.initMenu(data);
            });
        })
        .catch(() => {
            window.bootbox.alert('加载文件失败！');
        });
}

export function refreshActionLibraries(): void {
    let actionFiles = joinLibraryFiles(actionLibraries);
    if (actionFiles === '' || actionFiles.length < 2) {
        actionFiles = 'builtinactions';
    }
    formPost<unknown[]>('/common/loadXml', { files: actionFiles })
        .then((data: unknown[]) => {
            window._ruleforgeEditorActionLibraries = data;
            actionTypeArray.forEach(item => {
                item.initMenu(data);
            });
        })
        .catch(() => {
            window.bootbox.alert('加载文件失败！');
        });
}

export function refreshConstantLibraries(): void {
    const constantFiles = joinLibraryFiles(constantLibraries);
    if (constantFiles === '' || constantFiles.length < 2) {
        return;
    }
    formPost<unknown[]>('/common/loadXml', { files: constantFiles })
        .then((data: unknown[]) => {
            window._ruleforgeEditorConstantLibraries = data;
            constantValueArray.forEach(item => {
                item.initMenu(data);
            });
        })
        .catch(() => {
            window.bootbox.alert('加载文件失败！');
        });
}

export function refreshFunctionLibraries(): void {
    httpGet<unknown[]>('/common/loadFunctions')
        .then((data: unknown[]) => {
            (window as unknown as Record<string, unknown>)._ruleforgeEditorFunctionLibraries = data;
            functionValueArray.forEach(item => {
                item.initMenu(data);
            });
        })
        .catch(() => {
            window.bootbox.alert('加载函数失败！');
        });
}

// --- Initialize ruleforge global namespace (used by unconverted prototype classes) ---
if (!(window as unknown as Record<string, unknown>).ruleforge) {
    (window as unknown as Record<string, unknown>).ruleforge = {};
}

// --- Backward-compatible window globals ---

(window as unknown as Record<string, unknown>)._ConstantValueArray = constantValueArray;
(window as unknown as Record<string, unknown>)._ActionTypeArray = actionTypeArray;
(window as unknown as Record<string, unknown>)._VariableValueArray = variableValueArray;
(window as unknown as Record<string, unknown>)._ParameterValueArray = parameterValueArray;
(window as unknown as Record<string, unknown>)._FunctionValueArray = functionValueArray;
(window as unknown as Record<string, unknown>).actionLibraries = actionLibraries;
(window as unknown as Record<string, unknown>).variableLibraries = variableLibraries;
(window as unknown as Record<string, unknown>).constantLibraries = constantLibraries;
(window as unknown as Record<string, unknown>).parameterLibraries = parameterLibraries;
(window as unknown as Record<string, unknown>).generateContainer = generateContainer;
(window as unknown as Record<string, unknown>).refreshParameterLibraries = refreshParameterLibraries;
(window as unknown as Record<string, unknown>).refreshVariableLibraries = refreshVariableLibraries;
(window as unknown as Record<string, unknown>).refreshActionLibraries = refreshActionLibraries;
(window as unknown as Record<string, unknown>).refreshConstantLibraries = refreshConstantLibraries;
(window as unknown as Record<string, unknown>).refreshFunctionLibraries = refreshFunctionLibraries;
