import {useSearchParams} from 'react-router-dom';
import LibraryEditor from './LibraryEditor';

/**
 * V1 库编辑器本体。
 * 可被路由壳或应用内标签宿主渲染 — file 从 props 传入,不依赖 react-router。
 */
export function V1LibraryEditor({file}: {file?: string}) {
    return <LibraryEditor file={file}/>;
}

/**
 * V1 库编辑器路由。/app/v1-library?file=/proj/x.v1lib.json(从项目树"V1库"分类进入)。
 * 本壳只做 searchParams 取值,编辑器逻辑全部在 {@link V1LibraryEditor}。
 */
export default function LibraryEditorRoute() {
    const [params] = useSearchParams();
    return <V1LibraryEditor file={params.get('file') || undefined}/>;
}
