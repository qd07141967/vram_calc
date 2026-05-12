package com.wmdoc.vram2.calculator;

import com.wmdoc.vram2.config.SpecResourceParseConfig;
import com.wmdoc.vram2.config.VramInfoConfig;
import com.wmdoc.vram2.model.SpecResourceInput;
import com.wmdoc.vram2.model.SpecificationInput;
import com.wmdoc.vram2.model.SpecificationVramEvaluationRequest;
import com.wmdoc.vram2.model.SpecificationVramEvaluationResult;
import com.wmdoc.vram2.model.VramRequirementRequest;
import com.wmdoc.vram2.model.VramRequirementResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 双阶段显存计算器 JUnit 测试。 */
class VramCalculatorTest {
    /** 验证阶段一 GQA 显存明细计算。 */
    @Test
    void calculatesStageOneRequirement() {
        VramRequirementResult result = new VramRequirementCalculator(VramInfoConfig.defaults()).calculate(validRequirementRequest());

        assertClose(16.09, result.getWeightMemoryGib(), 0.01);
        assertClose(128.0, result.getKvCacheGib(), 0.01);
        assertClose(145.59, result.getBaseVramGib(), 0.01);
        assertClose(14.56, result.getOverallReserveGib(), 0.01);
        assertClose(0.10, result.getOverallReserveRatio(), 0.001);
        assertClose(160.15, result.getRequiredVramGib(), 0.01);
    }

    /** 验证默认配置里的 min(...) 表达式可以被 Aviator 正确执行。 */
    @Test
    void calculatesSlidingWindowExpression() {
        VramRequirementResult result = new VramRequirementCalculator(VramInfoConfig.defaults()).calculate(new VramRequirementRequest(
                "mqa_sliding_window",
                "int4",
                "bf16",
                "chat",
                "llm",
                List.of("text"),
                List.of("text"),
                Map.of(
                        "total_params", 1_000_000_000.0,
                        "concurrency_b", 2.0,
                        "context_length_s", 4096.0,
                        "overall_reserve_ratio", 0.10,
                        "num_hidden_layers", 4.0,
                        "k_head_dim", 64.0,
                        "v_head_dim", 64.0,
                        "sliding_window", 1024.0)));

        // 2 * min(4096, 1024) * 4 * (64 + 64) * 2 bytes.
        assertClose(0.00195, result.getKvCacheGib(), 0.00001);
    }

    /** 验证新增架构只需要配置 Aviator 表达式，不需要修改计算器代码。 */
    @Test
    void calculatesConfigOnlyArchitectureExpression() {
        VramInfoConfig config = new VramInfoConfig(
                Map.of("toy_expr", new VramInfoConfig.ArchitectureFormulaConfig(
                        "toy_expr",
                        Set.of("total_params", "overall_reserve_ratio", "token_count", "width"),
                        "token_count * width * kv_cache_dtype_bytes",
                        "token_count x width x kv_cache_dtype_bytes")),
                Map.of(
                        "bf16", new VramInfoConfig.QuantConfig("bf16", 16, 2.0, 1.0, Set.of("weight", "kv")),
                        "int4", new VramInfoConfig.QuantConfig("int4", 4, 0.5, 1.08, Set.of("weight"))),
                Map.of("chat|llm|text|text", new VramInfoConfig.FrameworkReserveConfig(1.5, 0.06)));

        VramRequirementResult result = new VramRequirementCalculator(config).calculate(new VramRequirementRequest(
                "toy_expr",
                "int4",
                "bf16",
                "chat",
                "llm",
                List.of("text"),
                List.of("text"),
                Map.of(
                        "total_params", 1_000_000_000.0,
                        "overall_reserve_ratio", 0.10,
                        "token_count", 1024.0,
                        "width", 256.0)));

        // token_count * width * bf16_bytes = 1024 * 256 * 2 bytes.
        assertClose(0.00049, result.getKvCacheGib(), 0.00001);
        assertEquals("token_count x width x kv_cache_dtype_bytes", result.getKvFormula());
    }

    /** 验证阶段二一次性输出 single_instance、multi_instance、cluster 三种结果。 */
    @Test
    void evaluatesAllDeploymentModes() {
        VramRequirementResult requirement = new VramRequirementResult(
                16,
                2,
                120,
                138,
                13.8,
                0.1,
                151.8,
                "test");
        SpecificationVramEvaluationResult result = new SpecificationVramEvaluator(SpecResourceParseConfig.defaults())
                .evaluate(new SpecificationVramEvaluationRequest(
                        requirement,
                        List.of(spec("spec-a", "A800-80G-1", 1, 80), spec("spec-b", "A800-80G-8", 8, 80))));

        SpecificationVramEvaluationResult.SpecEvaluation specA = result.getResults().get(0);
        assertFalse(specA.getSingleInstance().isCalculable(), "spec-a single_instance should be insufficient");
        assertEquals("insufficient_single_node_vram", specA.getSingleInstance().getReason());
        assertEquals(3, specA.getMultiInstance().getRequiredNodes());
        assertClose(63.8, specA.getMultiInstance().getVramPerNodeGib(), 0.01);
        assertClose(240.0, specA.getMultiInstance().getConsumedTotalVramGib(), 0.01);
        assertClose(191.4, specA.getMultiInstance().getActualUsedVramGib(), 0.01);
        assertClose(0.7975, specA.getMultiInstance().getVramUsageRatio(), 0.001);
        assertEquals(2, specA.getCluster().getRequiredNodes());
        assertClose(0.94875, specA.getCluster().getVramUsageRatio(), 0.001);

        SpecificationVramEvaluationResult.SpecEvaluation specB = result.getResults().get(1);
        assertTrue(specB.getSingleInstance().isCalculable(), "spec-b single_instance should be calculable");
        assertEquals(1, specB.getSingleInstance().getRequiredNodes());
        assertClose(0.2375, specB.getSingleInstance().getVramUsageRatio(), 0.001);
        assertEquals(1, specB.getMultiInstance().getRequiredNodes());
        assertEquals(1, specB.getCluster().getRequiredNodes());
    }

    /** 验证规格缺少单卡显存时，三种部署结果都会返回不可计算原因。 */
    @Test
    void rejectsMissingSpecResource() {
        VramRequirementResult requirement = new VramRequirementResult(16, 2, 120, 138, 13.8, 0.1, 151.8, "test");
        SpecificationInput badSpec = new SpecificationInput(
                "bad",
                "bad-model",
                null,
                List.of(new SpecResourceInput("gpu", BigDecimal.valueOf(8), "card", true, null)));

        SpecificationVramEvaluationResult result = new SpecificationVramEvaluator(SpecResourceParseConfig.defaults())
                .evaluate(new SpecificationVramEvaluationRequest(requirement, List.of(badSpec)));

        assertEquals("missing_gpu_memory_gib_per_card", result.getResults().get(0).getSingleInstance().getReason());
        assertEquals("missing_gpu_memory_gib_per_card", result.getResults().get(0).getMultiInstance().getReason());
        assertEquals("missing_gpu_memory_gib_per_card", result.getResults().get(0).getCluster().getReason());
    }

    /** 构造阶段一合法请求。 */
    private static VramRequirementRequest validRequirementRequest() {
        return new VramRequirementRequest(
                "gqa",
                "int4",
                "bf16",
                "chat",
                "llm",
                List.of("text"),
                List.of("text"),
                Map.of(
                        "total_params", 32_000_000_000.0,
                        "concurrency_b", 16.0,
                        "context_length_s", 32_768.0,
                        "overall_reserve_ratio", 0.10,
                        "num_hidden_layers", 64.0,
                        "num_kv_heads", 8.0,
                        "k_head_dim", 128.0,
                        "v_head_dim", 128.0));
    }

    /** 构造规格测试对象。 */
    private static SpecificationInput spec(String id, String model, int gpuCount, int gpuMemoryGib) {
        return new SpecificationInput(
                id,
                model,
                null,
                List.of(
                        new SpecResourceInput("gpu", BigDecimal.valueOf(gpuCount), "card", true, null),
                        new SpecResourceInput("gpu_memory", BigDecimal.valueOf(gpuMemoryGib), "GiB", false, null)));
    }

    /** 断言两个浮点数在给定误差范围内近似相等。 */
    private static void assertClose(double expected, double actual, double tolerance) {
        assertEquals(expected, actual, tolerance);
    }
}
