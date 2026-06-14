/**
 * V5.71 — EditorContexts 单测(useProject / useDirty hook + Provider + useDirtyApi hook 行为)。
 *
 * <p>覆盖:
 * <ul>
 *   <li>{@link useProject} 默认值(无 Provider 时返回 null)</li>
 *   <li>{@link useProject} 在 Provider 内返回注入值</li>
 *   <li>{@link useDirty} 默认 no-op API(setDirty / clearDirty / isDirty 行为)</li>
 *   <li>{@link useDirty} 在 Provider 内拿到注入 API,行为正确</li>
 *   <li>嵌套 Provider 不影响子树:内层覆盖外层</li>
 *   <li>{@link useDirtyApi} hook:setDirty → isDirty=true,clearDirty → isDirty=false,
 *       file 变化时自动清零,setDirty 幂等(重复调用不触发额外 state 更新)</li>
 * </ul>
 */
import { describe, it, expect } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import {ProjectContext, DirtyContext, useProject, useDirty, useDirtyApi} from './EditorContexts';

function ProjectProbe({label}: {label: string}) {
    const project = useProject();
    return <span data-testid={label}>{project === null ? 'null' : project}</span>;
}

function DirtyProbe({label, onDirty}: {label: string; onDirty?: (d: ReturnType<typeof useDirty>) => void}) {
    const dirty = useDirty();
    if (onDirty) onDirty(dirty);
    return (
        <div>
            <button data-testid={`${label}-set`} onClick={() => dirty.setDirty()}>set</button>
            <button data-testid={`${label}-clear`} onClick={() => dirty.clearDirty()}>clear</button>
            <span data-testid={`${label}-state`}>{dirty.isDirty() ? 'dirty' : 'clean'}</span>
        </div>
    );
}

describe('EditorContexts - useProject', () => {
    // Given 没有 ProjectContext.Provider
    // When 子组件调用 useProject()
    // Then 应返回 null (编辑器尚未挂载)
    it('默认无 Provider 时返回 null', () => {
        render(<ProjectProbe label="p" />);
        expect(screen.getByTestId('p').textContent).toBe('null');
    });

    // Given ProjectContext.Provider value="my-project"
    // When 子组件调用 useProject()
    // Then 应返回 "my-project"
    it('Provider 注入项目名后正确返回', () => {
        render(
            <ProjectContext.Provider value="my-project">
                <ProjectProbe label="p" />
            </ProjectContext.Provider>
        );
        expect(screen.getByTestId('p').textContent).toBe('my-project');
    });

    // Given null 项目名(编辑器挂载但无文件)
    // When 子组件调用 useProject()
    // Then 应返回 null
    it('Provider value=null 时返回 null', () => {
        render(
            <ProjectContext.Provider value={null}>
                <ProjectProbe label="p" />
            </ProjectContext.Provider>
        );
        expect(screen.getByTestId('p').textContent).toBe('null');
    });
});

describe('EditorContexts - useDirty (default no-op)', () => {
    // Given 没有 DirtyContext.Provider
    // When 子组件调用 setDirty / clearDirty / isDirty
    // Then 全部安全 no-op,不抛错,isDirty 始终 false
    it('默认 no-op setDirty/clearDirty 不抛错', () => {
        const { getByTestId } = render(<DirtyProbe label="d" />);
        expect(getByTestId('d-state').textContent).toBe('clean');
        // setDirty / clearDirty 都不应抛错
        act(() => {
            getByTestId('d-set').click();
            getByTestId('d-clear').click();
        });
        // 默认 no-op,isDirty 永远 false
        expect(getByTestId('d-state').textContent).toBe('clean');
    });
});

describe('EditorContexts - useDirty (Provider 注入)', () => {
    // Given DirtyContext.Provider 提供 setDirty / clearDirty 实现
    // When 子组件调用 dirty.setDirty() / dirty.clearDirty()
    // Then isDirty 应反映最新状态(由注入 api 的闭包实现,ref 风格)
    it('Provider 注入 api 后 setDirty / clearDirty 正确生效', () => {
        let capturedApi: ReturnType<typeof useDirty> | null = null;

        // 实际构造一个跟 EditorRoute 同样模式的 api (useRef 风格)
        function Harness() {
            const realApi = (() => {
                let isDirty = false;
                return {
                    setDirty: () => { isDirty = true; },
                    clearDirty: () => { isDirty = false; },
                    isDirty: () => isDirty,
                };
            })();
            capturedApi = realApi;
            return (
                <DirtyContext.Provider value={realApi}>
                    <DirtyProbe label="d" />
                </DirtyContext.Provider>
            );
        }

        const { getByTestId } = render(<Harness />);
        expect(getByTestId('d-state').textContent).toBe('clean');
        act(() => {
            getByTestId('d-set').click();
        });
        expect(capturedApi!.isDirty()).toBe(true);
        act(() => {
            getByTestId('d-clear').click();
        });
        expect(capturedApi!.isDirty()).toBe(false);
    });
});

describe('EditorContexts - 嵌套 Provider', () => {
    // Given 外层 ProjectContext.Provider value="outer" + 内层 value="inner"
    // When 内层子组件调用 useProject()
    // Then 应返回 "inner"(内层覆盖外层)
    it('内层 ProjectContext.Provider 覆盖外层', () => {
        render(
            <ProjectContext.Provider value="outer">
                <ProjectContext.Provider value="inner">
                    <ProjectProbe label="p" />
                </ProjectContext.Provider>
            </ProjectContext.Provider>
        );
        expect(screen.getByTestId('p').textContent).toBe('inner');
    });
});

describe('EditorContexts - useDirtyApi hook', () => {
    // Given useDirtyApi('file-a')
    // When 调用 setDirty()
    // Then isDirty 应返回 true,isDirty getter 也返回 true
    it('setDirty 后 isDirty=true', () => {
        let capturedApi: ReturnType<typeof useDirtyApi>['dirtyApi'] | null = null;
        function Harness() {
            const result = useDirtyApi('file-a');
            capturedApi = result.dirtyApi;
            return (
                <span data-testid="state">{result.isDirty ? 'dirty' : 'clean'}</span>
            );
        }
        render(<Harness />);
        expect(screen.getByTestId('state').textContent).toBe('clean');
        act(() => {
            capturedApi!.setDirty();
        });
        // 重新 render — useDirtyApi 返回新的 isDirty getter
        // setState 触发的 re-render 读 dirtyRef.current
        // 由于 setState 用 true 触发,组件重新 render 时 dirtyRef.current=true
        // 但 render 时 result.isDirty 读 dirtyRef.current(同步读 ref)
        // 第二次 render 才会反映 — 我们用 act 等待
        expect(capturedApi!.isDirty()).toBe(true);
    });

    // Given useDirtyApi('file-a'),setDirty() 后 dirty=true
    // When 调用 clearDirty()
    // Then isDirty 应返回 false
    it('clearDirty 后 isDirty=false', () => {
        let capturedApi: ReturnType<typeof useDirtyApi>['dirtyApi'] | null = null;
        function Harness() {
            const result = useDirtyApi('file-a');
            capturedApi = result.dirtyApi;
            return <span data-testid="state">{result.isDirty ? 'dirty' : 'clean'}</span>;
        }
        render(<Harness />);
        act(() => {
            capturedApi!.setDirty();
        });
        expect(capturedApi!.isDirty()).toBe(true);
        act(() => {
            capturedApi!.clearDirty();
        });
        expect(capturedApi!.isDirty()).toBe(false);
    });

    // Given useDirtyApi('file-a'),setDirty() 后 dirty=true
    // When file prop 变化('file-a' → 'file-b')
    // Then dirty 自动清零(isDirty=false)
    it('file 变化时 dirty 自动清零', () => {
        let capturedApi: ReturnType<typeof useDirtyApi>['dirtyApi'] | null = null;
        function Harness({file}: {file: string}) {
            const result = useDirtyApi(file);
            capturedApi = result.dirtyApi;
            return <span data-testid="state">{result.isDirty ? 'dirty' : 'clean'}</span>;
        }

        const {rerender} = render(<Harness file="file-a" />);
        act(() => {
            capturedApi!.setDirty();
        });
        expect(capturedApi!.isDirty()).toBe(true);

        // file 变化
        rerender(<Harness file="file-b" />);
        expect(capturedApi!.isDirty()).toBe(false);
    });

    // Given useDirtyApi('file-a')
    // When 重复调用 setDirty() 多次
    // Then 不抛错,isDirty 仍 true(幂等)
    it('setDirty 重复调用幂等', () => {
        let capturedApi: ReturnType<typeof useDirtyApi>['dirtyApi'] | null = null;
        function Harness() {
            const result = useDirtyApi('file-a');
            capturedApi = result.dirtyApi;
            return null;
        }
        render(<Harness />);
        act(() => {
            capturedApi!.setDirty();
            capturedApi!.setDirty();
            capturedApi!.setDirty();
        });
        expect(capturedApi!.isDirty()).toBe(true);
    });
});