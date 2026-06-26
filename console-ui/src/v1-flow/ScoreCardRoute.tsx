import {useSearchParams} from 'react-router-dom';
import ScoreCardEditor from './ScoreCardEditor';

/**
 * V1 评分卡编辑器路由。/app/v1-scorecard?file=/proj/x.v1sc.json(从项目树"V1评分卡"分类进入)。
 */
export default function ScoreCardRoute() {
    const [params] = useSearchParams();
    return <ScoreCardEditor file={params.get('file') || undefined}/>;
}
