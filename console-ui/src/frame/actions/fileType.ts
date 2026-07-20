// V7.24:从 frame/action.ts 拆分 — 文件类型 → 中文类型名映射(buildType)
import {alert} from '@/utils/modal';

export function buildType(fileType: string): string {
    let pos = fileType.indexOf(':');
    if (pos > -1) {
        fileType = fileType.substring(0, pos);
    }
    let type: string | undefined;
    switch (fileType) {
        // V7.23:vl.xml/cl.xml/pl.xml/al.xml case 删除 —— 老 4 库"新建"入口随编辑器下线移除,
        //   与老 urule 类型一样走下方 "Unknow file type" 抛错防误用
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
        // V7.4/V7.5:V1 库/规则独立文件(fileType 用统一后缀,跟 v1flow.json 同约定;
        //   后端按 FileTypeUtils 后缀归类,裸类型名后缀会让新文件在树里不可见)
        case "v1lib.json":
            type = "V1 库";
            break;
        case "v1rs.json":
            type = "V1 规则集";
            break;
        case "v1dt.json":
            type = "V1 决策表";
            break;
        case "v1sc.json":
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
