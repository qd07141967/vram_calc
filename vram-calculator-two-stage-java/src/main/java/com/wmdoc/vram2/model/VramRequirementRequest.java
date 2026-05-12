package com.wmdoc.vram2.model;

import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 阶段一输入对象，用于计算模型显存需求明细。 */
@Getter
@ToString
public final class VramRequirementRequest {
    /** 架构公式类型，例如 gqa、mla。 */
    private final String architecture;
    /** 模型权重量化方式。 */
    private final String modelQuant;
    /** KV Cache 量化方式。 */
    private final String kvQuant;
    /** 模型主类型。 */
    private final String modelCategory;
    /** 模型子类型。 */
    private final String modelSubtype;
    /** 输入模态。 */
    private final List<String> inputModalities;
    /** 输出模态。 */
    private final List<String> outputModalities;
    /** 所有代入公式的数值参数。 */
    private final Map<String, Double> paramMap;

    /** 创建阶段一显存需求计算请求，并标准化配置匹配字段。 */
    public VramRequirementRequest(
            String architecture,
            String modelQuant,
            String kvQuant,
            String modelCategory,
            String modelSubtype,
            List<String> inputModalities,
            List<String> outputModalities,
            Map<String, Double> paramMap) {
        this.architecture = normalize(architecture);
        this.modelQuant = normalize(modelQuant);
        this.kvQuant = normalize(kvQuant);
        this.modelCategory = normalize(modelCategory);
        this.modelSubtype = normalize(modelSubtype);
        this.inputModalities = List.copyOf(inputModalities);
        this.outputModalities = List.copyOf(outputModalities);
        this.paramMap = Map.copyOf(Objects.requireNonNull(paramMap, "paramMap"));
    }

    /** 统一字符串 key，避免大小写差异影响配置匹配。 */
    private static String normalize(String value) {
        return Objects.requireNonNull(value, "value").trim().toLowerCase();
    }
}
