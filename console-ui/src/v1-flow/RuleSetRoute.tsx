import {useSearchParams} from 'react-router-dom';
import RuleSetEditor from './RuleSetEditor';

/**
 * V1 规则集编辑器路由。/app/v1-ruleset?file=/proj/x.v1rs.json(从项目树"V1规则集"分类进入)。
 */
export default function RuleSetRoute() {
    const [params] = useSearchParams();
    return <RuleSetEditor file={params.get('file') || undefined}/>;
}
