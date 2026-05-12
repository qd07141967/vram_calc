package com.wmdoc.vram2.config;

import lombok.Getter;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 阶段一显存计算配置，开发阶段统一用一个配置对象维护公式、量化方式和框架预留规则。 */
public final class VramInfoConfig {
    /** 架构公式配置：key 为架构名，value 为该架构的必填参数和 Aviator 表达式。 */
    private final Map<String, ArchitectureFormulaConfig> architectureFormulaConfig;
    /** 量化配置：key 为量化名，value 为位数、字节数和量化元数据开销。 */
    private final Map<String, QuantConfig> quantConfig;
    /** 框架预留配置：key 为模型类型和模态组合，value 为保底值和比例。 */
    private final Map<String, FrameworkReserveConfig> frameworkReserveConfig;

    /** 创建显存配置对象。 */
    public VramInfoConfig(
            Map<String, ArchitectureFormulaConfig> architectureFormulaConfig,
            Map<String, QuantConfig> quantConfig,
            Map<String, FrameworkReserveConfig> frameworkReserveConfig) {
        this.architectureFormulaConfig = Map.copyOf(architectureFormulaConfig);
        this.quantConfig = Map.copyOf(quantConfig);
        this.frameworkReserveConfig = Map.copyOf(frameworkReserveConfig);
    }

    /** 根据架构名称获取公式配置，返回 null 表示该架构尚未配置。 */
    public ArchitectureFormulaConfig architecture(String architecture) {
        return architectureFormulaConfig.get(architecture);
    }

    /** 根据量化名称获取量化配置，返回 null 表示该量化方式尚未配置。 */
    public QuantConfig quant(String quantName) {
        return quantConfig.get(quantName);
    }

    /** 根据模型类型和模态获取框架预留配置，优先精确匹配，找不到则使用通配匹配。 */
    public FrameworkReserveConfig frameworkReserve(
            String category,
            String subtype,
            List<String> inputModalities,
            List<String> outputModalities) {
        String exactKey = frameworkKey(category, subtype, inputModalities, outputModalities);
        FrameworkReserveConfig exact = frameworkReserveConfig.get(exactKey);
        if (exact != null) {
            return exact;
        }
        return frameworkReserveConfig.get(frameworkKey(category, subtype, List.of("*"), List.of("*")));
    }

    /** 构造默认配置，覆盖当前文档中的主要架构公式、量化方式和框架预留规则。 */
    public static VramInfoConfig defaults() {
        return new VramInfoConfig(
                defaultArchitectureFormulaConfig(),
                defaultQuantConfig(),
                defaultFrameworkReserveConfig());
    }

    /** 默认架构公式配置；新增架构时优先只在这里增加表达式，而不是改计算器逻辑。 */
    private static Map<String, ArchitectureFormulaConfig> defaultArchitectureFormulaConfig() {
        Set<String> common = Set.of("total_params", "concurrency_b", "context_length_s", "overall_reserve_ratio");
        return Map.of(
                "gqa", new ArchitectureFormulaConfig(
                        "gqa",
                        union(common, "num_hidden_layers", "num_kv_heads", "k_head_dim", "v_head_dim"),
                        "concurrency_b * context_length_s * num_hidden_layers * num_kv_heads "
                                + "* (k_head_dim + v_head_dim) * kv_cache_dtype_bytes",
                        "B x S x num_hidden_layers x num_kv_heads x (k_head_dim + v_head_dim) x kv_cache_dtype_bytes"),
                "mha", new ArchitectureFormulaConfig(
                        "mha",
                        union(common, "num_hidden_layers", "num_kv_heads", "k_head_dim", "v_head_dim"),
                        "concurrency_b * context_length_s * num_hidden_layers * num_kv_heads "
                                + "* (k_head_dim + v_head_dim) * kv_cache_dtype_bytes",
                        "B x S x num_hidden_layers x num_kv_heads x (k_head_dim + v_head_dim) x kv_cache_dtype_bytes"),
                "mla", new ArchitectureFormulaConfig(
                        "mla",
                        union(common, "num_hidden_layers", "kv_lora_rank", "qk_rope_head_dim"),
                        "concurrency_b * context_length_s * num_hidden_layers "
                                + "* (kv_lora_rank + qk_rope_head_dim) * kv_cache_dtype_bytes",
                        "B x S x num_hidden_layers x (kv_lora_rank + qk_rope_head_dim) x kv_cache_dtype_bytes"),
                "mqa_sliding_window", new ArchitectureFormulaConfig(
                        "mqa_sliding_window",
                        union(common, "num_hidden_layers", "k_head_dim", "v_head_dim", "sliding_window"),
                        "concurrency_b * min(context_length_s, sliding_window) * num_hidden_layers "
                                + "* (k_head_dim + v_head_dim) * kv_cache_dtype_bytes",
                        "B x min(S, sliding_window) x num_hidden_layers x (k_head_dim + v_head_dim) x kv_cache_dtype_bytes"),
                "hybrid_gqa_linear", new ArchitectureFormulaConfig(
                        "hybrid_gqa_linear",
                        union(common,
                                "full_attention", "num_kv_heads", "k_head_dim", "v_head_dim",
                                "linear_attention", "num_heads", "feature_dim", "value_dim", "state_dtype_bytes"),
                        "concurrency_b * context_length_s * full_attention * num_kv_heads "
                                + "* (k_head_dim + v_head_dim) * kv_cache_dtype_bytes "
                                + "+ concurrency_b * linear_attention "
                                + "* (num_heads * feature_dim * value_dim + num_heads * feature_dim) * state_dtype_bytes",
                        "B x S x gqa_kv_cache_bytes_per_token + B x linear_state_bytes_per_sequence"),
                "none", new ArchitectureFormulaConfig(
                        "none",
                        Set.of("total_params", "overall_reserve_ratio"),
                        "0",
                        "0"));
    }

    /** 默认量化配置，quantOverheadFactor 用于覆盖 scale/zero point 等量化元数据开销。 */
    private static Map<String, QuantConfig> defaultQuantConfig() {
        return Map.of(
                "fp32", new QuantConfig("fp32", 32, 4.0, 1.0, Set.of("weight", "kv")),
                "fp16", new QuantConfig("fp16", 16, 2.0, 1.0, Set.of("weight", "kv")),
                "bf16", new QuantConfig("bf16", 16, 2.0, 1.0, Set.of("weight", "kv")),
                "fp8", new QuantConfig("fp8", 8, 1.0, 1.0, Set.of("weight", "kv")),
                "int8", new QuantConfig("int8", 8, 1.0, 1.03, Set.of("weight")),
                "int4", new QuantConfig("int4", 4, 0.5, 1.08, Set.of("weight")));
    }

    /** 默认框架预留配置。 */
    private static Map<String, FrameworkReserveConfig> defaultFrameworkReserveConfig() {
        return Map.of(
                frameworkKey("chat", "llm", List.of("text"), List.of("text")),
                new FrameworkReserveConfig(1.5, 0.06),
                frameworkKey("chat", "vlm", List.of("*"), List.of("text")),
                new FrameworkReserveConfig(3.0, 0.08),
                frameworkKey("embedding", "embedding", List.of("*"), List.of("*")),
                new FrameworkReserveConfig(1.0, 0.04),
                frameworkKey("reranker", "reranker", List.of("*"), List.of("*")),
                new FrameworkReserveConfig(1.0, 0.05));
    }

    /** 合并公共必填字段和架构专属必填字段。 */
    private static Set<String> union(Set<String> base, String... extra) {
        LinkedHashSet<String> values = new LinkedHashSet<>(base);
        values.addAll(List.of(extra));
        return Set.copyOf(values);
    }

    /** 生成框架预留匹配 key。 */
    private static String frameworkKey(String category, String subtype, List<String> inputs, List<String> outputs) {
        return category + "|" + subtype + "|" + String.join(",", inputs) + "|" + String.join(",", outputs);
    }

    /** 架构公式配置。 */
    @Getter
    @ToString
    public static final class ArchitectureFormulaConfig {
        /** 架构名称。 */
        private final String architecture;
        /** 该架构要求 paramMap 必须包含的字段；表达式里的派生变量不需要放在这里。 */
        private final Set<String> requiredParams;
        /** Aviator 表达式，计算结果单位必须是 bytes。 */
        private final String kvExpression;
        /** 面向结果展示和排查的公式说明，不参与计算。 */
        private final String kvFormula;

        /** 创建架构公式配置。 */
        public ArchitectureFormulaConfig(String architecture, Set<String> requiredParams, String kvExpression, String kvFormula) {
            this.architecture = architecture;
            this.requiredParams = Set.copyOf(requiredParams);
            this.kvExpression = kvExpression;
            this.kvFormula = kvFormula;
        }
    }

    /** 量化配置。 */
    @Getter
    @ToString
    public static final class QuantConfig {
        /** 量化名称。 */
        private final String quantName;
        /** 量化位数。 */
        private final int bits;
        /** 每个值占用的字节数。 */
        private final double bytesPerValue;
        /** 权重量化额外开销系数。 */
        private final double quantOverheadFactor;
        /** 适用范围，例如 weight 或 kv。 */
        private final Set<String> scope;

        /** 创建量化配置。 */
        public QuantConfig(String quantName, int bits, double bytesPerValue, double quantOverheadFactor, Set<String> scope) {
            this.quantName = quantName;
            this.bits = bits;
            this.bytesPerValue = bytesPerValue;
            this.quantOverheadFactor = quantOverheadFactor;
            this.scope = Set.copyOf(scope);
        }

        /** 判断该量化方式是否支持指定用途。 */
        public boolean supports(String useCase) {
            return scope.contains(useCase);
        }
    }

    /** 框架预留配置。 */
    @Getter
    @ToString
    public static final class FrameworkReserveConfig {
        /** 框架预留保底值，单位 GiB。 */
        private final double floorGib;
        /** 框架预留比例。 */
        private final double ratio;

        /** 创建框架预留配置。 */
        public FrameworkReserveConfig(double floorGib, double ratio) {
            this.floorGib = floorGib;
            this.ratio = ratio;
        }
    }
}
