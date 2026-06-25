import {useSearchParams} from 'react-router-dom';
import LibraryEditor from './LibraryEditor';

/**
 * V1 库编辑器路由。/app/v1-library?file=/proj/x.v1lib.json(从项目树"V1库"分类进入)。
 */
export default function LibraryEditorRoute() {
    const [params] = useSearchParams();
    return <LibraryEditor file={params.get('file') || undefined}/>;
}
