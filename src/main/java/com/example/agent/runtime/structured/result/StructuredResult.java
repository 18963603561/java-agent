package com.example.agent.runtime.structured.result;

import com.example.agent.runtime.structured.ResultKind;
import com.example.agent.runtime.structured.data.DefaultStructuredData;
import com.example.agent.runtime.structured.data.StructuredData;

import java.util.Map;

/**
 * 结构化结果统一外壳。
 *
 * <p>用途：承载步骤执行后的语义化结果，避免业务代码直接依赖原始输出结构。
 *
 * <p>边界条件：
 * 1. data 类型必须与 kind 语义匹配；
 * 2. data 建议使用稳定可演进的类型对象；
 * 3. schemaVersion 用于结构升级兼容控制。
 *
 * @param <T> 结构化数据类型，必须实现 StructuredData
 */
public class StructuredResult<T extends StructuredData> {

    /**
     * 结果语义类型。
     */
    private ResultKind kind;

    /**
     * 数据结构版本号。
     */
    private Integer schemaVersion;

    /**
     * 类型专属语义数据。
     */
    private T data;

    /**
     * 结构化结果质量信息。
     */
    private StructuredQuality quality;

    /**
     * 结构化引用信息。
     */
    private StructuredRefs refs;

    /**
     * 通用工厂方法。
     *
     * @param kind 结果语义类型
     * @param schemaVersion 数据结构版本
     * @param data 类型化语义数据
     * @return 结构化结果对象
     * @param <TData> 数据类型参数
     */
    public static <TData extends StructuredData> StructuredResult<TData> of(ResultKind kind,
                                                                            Integer schemaVersion,
                                                                            TData data) {
        StructuredResult<TData> result = new StructuredResult<>();
        result.setKind(kind);
        result.setSchemaVersion(schemaVersion);
        result.setData(data);
        return result;
    }

    /**
     * 默认数据实现工厂方法。
     *
     * @param kind 结果语义类型
     * @param schemaVersion 数据结构版本
     * @param values 默认键值数据
     * @return 使用默认实现的结构化结果
     */
    public static StructuredResult<DefaultStructuredData> defaultResult(ResultKind kind,
                                                                        Integer schemaVersion,
                                                                        Map<String, Object> values) {
        return of(kind, schemaVersion, DefaultStructuredData.from(values));
    }

    /**
     * 获取 data 的通用 Map 视图。
     *
     * @return data 的 Map 结构；当 data 为空时返回空 Map
     */
    public Map<String, Object> dataAsMap() {
        if (data == null) {
            return Map.of();
        }
        Map<String, Object> map = data.toMap();
        return map == null ? Map.of() : map;
    }

    public ResultKind getKind() {
        return kind;
    }

    public void setKind(ResultKind kind) {
        this.kind = kind;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public StructuredQuality getQuality() {
        return quality;
    }

    public void setQuality(StructuredQuality quality) {
        this.quality = quality;
    }

    public StructuredRefs getRefs() {
        return refs;
    }

    public void setRefs(StructuredRefs refs) {
        this.refs = refs;
    }
}

