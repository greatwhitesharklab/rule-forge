package com.ruleforge.v1.ast.library;

import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V1 库解析器(V7.4.1):Library → 执行期输入。
 * <ul>
 *   <li>{@link #deriveSchema(Library)} — vl 库 → fact Schema(字段定义),RuleAsset.schemaRef 引用时派生</li>
 *   <li>{@link #deriveParameters(Library...)} — pl/cl 库 → 会话参数 Map(key→value),喂 fireRules</li>
 * </ul>
 *
 * <p>vl 真共享:多 RuleAsset 引用同一 vl 库(schemaRef),fact schema 从 vl 派生而非内嵌;
 * 改 vl 库所有引用流程生效。pl/cl 同机制(agent 调研选项 A:复用参数通道)。
 */
public final class V1LibraryResolver {

    private V1LibraryResolver() {
    }

    /** vl 库 → Schema(name=库名,fields=entries)。 */
    public static Schema deriveSchema(Library vl) {
        Schema s = new Schema();
        s.setName(vl.getName());
        if (vl.getEntries() != null) {
            List<SchemaField> fields = new ArrayList<>();
            for (LibraryEntry e : vl.getEntries()) {
                SchemaField f = new SchemaField(e.getKey(), e.getDataType());
                f.setLabel(e.getLabel());
                fields.add(f);
            }
            s.setFields(fields);
        }
        return s;
    }

    /** pl/cl 库 → 参数 Map(所有 entries 的 key→value 合并;null 库跳过)。 */
    public static Map<String, Object> deriveParameters(Library... libs) {
        Map<String, Object> p = new HashMap<>();
        if (libs == null) {
            return p;
        }
        for (Library lib : libs) {
            if (lib == null || lib.getEntries() == null) {
                continue;
            }
            for (LibraryEntry e : lib.getEntries()) {
                p.put(e.getKey(), e.getValue());
            }
        }
        return p;
    }
}
