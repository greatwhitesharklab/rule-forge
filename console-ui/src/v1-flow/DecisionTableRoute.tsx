import {useSearchParams} from 'react-router-dom';
import DecisionTableEditor from './DecisionTableEditor';

/**
 * V1 决策表编辑器路由。/app/v1-decisiontable?file=/proj/x.v1dt.json(从项目树"V1决策表"分类进入)。
 */
export default function DecisionTableRoute() {
    const [params] = useSearchParams();
    return <DecisionTableEditor file={params.get('file') || undefined}/>;
}
