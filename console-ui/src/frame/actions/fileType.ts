// V7.24:从 frame/action.ts 拆分 — 文件类型 → 中文类型名映射(buildType)
import {alert} from '@/utils/modal';

export function buildType(fileType: string): string {
    let pos = fileType.indexOf(':');
    if (pos > -1) {
        fileType = fileType.substring(0, pos);
    }
    let type: string | undefined;
    switch (fileType) {
        case 'vl.xml':
            type = "变量库";
            break;
        case 'cl.xml':
            type = '常量库';
            break;
        case 'pl.xml':
            type = '参数库';
            break;
        case 'al.xml':
            type = '动作库';
            break;
        // V7.21:case 'rl.xml'(BPMN 决策流)已删除 — V1 决策流为唯一决策路径。
        // V6.20.0:DRL 规则
        case "drl":
            type = "DRL 规则";
            break;
        // V6.20.0 P3:DMN / PMML 标准决策模型
        case "dmn":
            type = "DMN 决策表(只读)";
            break;
        case "pmml":
            type = "PMML 模型(只读)";
            break;
        // V7.0.0->V7.5.1:V1 文件类型(buildType 的 case 必须与 OPEN_CREATE_FILE_DIALOG
        //   emit 的 fileType 值一致,否则 buildType 抛 "Unknow file type" 中断对话框)
        case "v1flow.json":
            type = "V1 决策流";
            break;
        case "V1Library":
            type = "V1 库";
            break;
        case "V1RuleSet":
            type = "V1 规则集";
            break;
        case "V1DecisionTable":
            type = "V1 决策表";
            break;
        case "V1ScoreCard":
            type = "V1 评分卡";
            break;
        // 兼容旧 .json 文件(老文件无 .v1flow.json 后缀,buildType 传 "json")
        case "json":
            type = "V1 决策流";
            break;
        // V7.7.2:"rp" case 删除 — 老 .rp 知识包废弃
    }
    if (!type) {
        const info = "Unknow file type :" + fileType;
        alert(info);
        throw info;
    }
    return type;
}
