package com.ruleforge.v1.ast;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.io.Reader;

/**
 * V1 资产 JSON 读写(Jackson)。.json 文件内容靠 {@link RuleAsset#getVersion()} 自识别。
 *
 * <p>多态反序列化靠 {@link NodeBase} / {@link FlowElement} 的 {@code @JsonSubTypes}
 * (JSON 的 {@code "type"} 字段路由子类)。无状态,线程安全(单例 ObjectMapper)。
 */
public final class RuleAssetIO {

    /** 单例 ObjectMapper(线程安全)。FAIL_ON_UNKNOWN 关闭,允许 JSON 加字段向前兼容。 */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private RuleAssetIO() {
    }

    /** 供其它模块复用同一配置的 ObjectMapper(如 CEL 校验器、前端 API)。 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** 从 JSON 字符串读 RuleAsset。 */
    public static RuleAsset read(String json) throws IOException {
        return MAPPER.readValue(json, RuleAsset.class);
    }

    /** 从 Reader 读。 */
    public static RuleAsset read(Reader reader) throws IOException {
        return MAPPER.readValue(reader, RuleAsset.class);
    }

    /** 从 InputStream 读。 */
    public static RuleAsset read(InputStream in) throws IOException {
        return MAPPER.readValue(in, RuleAsset.class);
    }

    /** 写 RuleAsset → JSON 字符串(pretty print)。 */
    public static String write(RuleAsset asset) throws IOException {
        return MAPPER.writeValueAsString(asset);
    }

    /** 写 RuleAsset → Writer。 */
    public static void write(RuleAsset asset, Writer writer) throws IOException {
        MAPPER.writeValue(writer, asset);
    }

    /** 写 RuleAsset → OutputStream。 */
    public static void write(RuleAsset asset, OutputStream out) throws IOException {
        MAPPER.writeValue(out, asset);
    }
}
