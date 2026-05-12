package com.wmdoc.vram2.calculator;

import com.googlecode.aviator.AviatorEvaluator;
import com.wmdoc.vram2.common.MemoryUnit;
import com.wmdoc.vram2.config.VramInfoConfig;
import com.wmdoc.vram2.model.VramRequirementRequest;
import com.wmdoc.vram2.model.VramRequirementResult;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 阶段一计算器：计算模型显存需求明细。 */
@RequiredArgsConstructor
public final class VramRequirementCalculator {
    /** 显存计算配置。 */
    private final VramInfoConfig config;

    /** 执行显存需求计算。 */
    public VramRequirementResult calculate(VramRequirementRequest request) {
        // 先校验配置和必填参数，避免 Aviator 求值时才暴露难读的变量缺失错误。
        validateTopLevel(request);
        VramInfoConfig.ArchitectureFormulaConfig architectureConfig = requireArchitecture(request.getArchitecture());
        validateRequiredParams(request.getParamMap(), architectureConfig);

        VramInfoConfig.QuantConfig modelQuant = requireQuant(request.getModelQuant(), "weight");
        VramInfoConfig.QuantConfig kvQuant = requireQuant(request.getKvQuant(), "kv");
        VramInfoConfig.FrameworkReserveConfig frameworkReserve = requireFrameworkReserve(request);

        Map<String, Double> params = request.getParamMap();
        double overallReserveRatio = required(params, "overall_reserve_ratio");

        // 权重显存 = 参数量 x 每参数字节数 x 量化额外开销。
        double weightMemoryBytes = required(params, "total_params")
                * modelQuant.getBytesPerValue()
                * modelQuant.getQuantOverheadFactor();
        double weightMemoryGib = MemoryUnit.bytesToGiB(weightMemoryBytes);

        // 框架预留 = max(保底值, 权重显存 x 预留比例)。
        double frameworkReserveGib = Math.max(
                frameworkReserve.getFloorGib(),
                weightMemoryGib * frameworkReserve.getRatio());

        // KV/状态显存由 Aviator 执行配置表达式，计算器本身不硬编码具体架构公式。
        KvResult kvResult = calculateKvByExpression(architectureConfig, params, kvQuant.getBytesPerValue());
        double kvCacheGib = MemoryUnit.bytesToGiB(kvResult.bytes());

        double baseVramGib = weightMemoryGib + frameworkReserveGib + kvCacheGib;
        double overallReserveGib = baseVramGib * overallReserveRatio;
        double requiredVramGib = baseVramGib + overallReserveGib;

        return new VramRequirementResult(
                weightMemoryGib,
                frameworkReserveGib,
                kvCacheGib,
                baseVramGib,
                overallReserveGib,
                overallReserveRatio,
                requiredVramGib,
                kvResult.formula());
    }

    /** 校验顶层字段是否完整。 */
    private void validateTopLevel(VramRequirementRequest request) {
        List<String> missing = new ArrayList<>();
        addIfBlank(missing, "architecture", request.getArchitecture());
        addIfBlank(missing, "model_quant", request.getModelQuant());
        addIfBlank(missing, "kv_quant", request.getKvQuant());
        addIfBlank(missing, "model_category", request.getModelCategory());
        addIfBlank(missing, "model_subtype", request.getModelSubtype());
        if (request.getInputModalities().isEmpty()) {
            missing.add("input_modalities");
        }
        if (request.getOutputModalities().isEmpty()) {
            missing.add("output_modalities");
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required fields: " + missing);
        }
    }

    /** 根据架构名称获取架构配置。 */
    private VramInfoConfig.ArchitectureFormulaConfig requireArchitecture(String architecture) {
        VramInfoConfig.ArchitectureFormulaConfig architectureConfig = config.architecture(architecture);
        if (architectureConfig == null) {
            throw new IllegalArgumentException("Unsupported architecture: " + architecture);
        }
        return architectureConfig;
    }

    /** 根据量化名称获取量化配置，并校验用途。 */
    private VramInfoConfig.QuantConfig requireQuant(String quantName, String useCase) {
        VramInfoConfig.QuantConfig quantConfig = config.quant(quantName);
        if (quantConfig == null) {
            throw new IllegalArgumentException("Unsupported quant: " + quantName);
        }
        if (!quantConfig.supports(useCase)) {
            throw new IllegalArgumentException("Quant " + quantName + " does not support " + useCase);
        }
        return quantConfig;
    }

    /** 获取框架预留配置。 */
    private VramInfoConfig.FrameworkReserveConfig requireFrameworkReserve(VramRequirementRequest request) {
        VramInfoConfig.FrameworkReserveConfig frameworkReserve = config.frameworkReserve(
                request.getModelCategory(),
                request.getModelSubtype(),
                request.getInputModalities(),
                request.getOutputModalities());
        if (frameworkReserve == null) {
            throw new IllegalArgumentException("No framework reserve config for "
                    + request.getModelCategory() + "/" + request.getModelSubtype());
        }
        return frameworkReserve;
    }

    /** 校验 paramMap 是否包含架构公式所需的所有输入参数。 */
    private void validateRequiredParams(Map<String, Double> params, VramInfoConfig.ArchitectureFormulaConfig config) {
        List<String> missing = config.getRequiredParams().stream()
                .filter(param -> !params.containsKey(param))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing param_map fields for " + config.getArchitecture() + ": " + missing);
        }
    }

    /** 使用 Aviator 表达式计算 KV Cache 或状态显存，表达式结果单位必须是 bytes。 */
    private KvResult calculateKvByExpression(
            VramInfoConfig.ArchitectureFormulaConfig architectureConfig,
            Map<String, Double> params,
            double kvBytesPerValue) {
        Map<String, Object> env = new HashMap<>(params);

        // kv_cache_dtype_bytes 是由 kvQuant 推导出的派生变量，不要求调用方重复放到 paramMap。
        env.put("kv_cache_dtype_bytes", kvBytesPerValue);

        Object expressionResult = AviatorEvaluator.execute(architectureConfig.getKvExpression(), env);
        double bytes = toDouble(expressionResult, architectureConfig);
        if (bytes < 0) {
            throw new IllegalArgumentException("KV expression result must be >= 0 for "
                    + architectureConfig.getArchitecture() + ", got " + bytes);
        }
        return new KvResult(bytes, architectureConfig.getKvFormula());
    }

    /** 将 Aviator 求值结果转换为 double，并在表达式返回非数字时给出明确错误。 */
    private static double toDouble(Object value, VramInfoConfig.ArchitectureFormulaConfig architectureConfig) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("KV expression for " + architectureConfig.getArchitecture()
                + " must return a number, got " + value);
    }

    /** 字符串为空时将字段名加入缺失列表。 */
    private static void addIfBlank(List<String> missing, String name, String value) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    /** 读取 paramMap 必填参数。 */
    private static double required(Map<String, Double> params, String key) {
        Double value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing param_map field: " + key);
        }
        return value;
    }

    /** KV 中间结果。 */
    private record KvResult(double bytes, String formula) {
    }
}
