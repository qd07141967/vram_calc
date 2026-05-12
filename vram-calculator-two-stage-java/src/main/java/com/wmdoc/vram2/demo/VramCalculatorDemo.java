package com.wmdoc.vram2.demo;

import com.wmdoc.vram2.calculator.SpecificationVramEvaluator;
import com.wmdoc.vram2.calculator.VramRequirementCalculator;
import com.wmdoc.vram2.config.SpecResourceParseConfig;
import com.wmdoc.vram2.config.VramInfoConfig;
import com.wmdoc.vram2.model.SpecResourceInput;
import com.wmdoc.vram2.model.SpecificationInput;
import com.wmdoc.vram2.model.SpecificationVramEvaluationRequest;
import com.wmdoc.vram2.model.SpecificationVramEvaluationResult;
import com.wmdoc.vram2.model.VramRequirementRequest;
import com.wmdoc.vram2.model.VramRequirementResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 双阶段显存计算器示例入口。 */
public final class VramCalculatorDemo {
    /** 运行阶段一和阶段二示例。 */
    public static void main(String[] args) {
        VramRequirementResult requirement = calculateRequirement();
        System.out.println(requirement);

        SpecificationVramEvaluationResult evaluation = evaluateSpecifications(requirement);
        System.out.println(evaluation);
    }

    /** 构造阶段一请求并计算显存需求明细。 */
    private static VramRequirementResult calculateRequirement() {
        VramRequirementRequest request = new VramRequirementRequest(
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

        return new VramRequirementCalculator(VramInfoConfig.defaults()).calculate(request);
    }

    /** 构造规格列表并一次性计算三种部署模式结果。 */
    private static SpecificationVramEvaluationResult evaluateSpecifications(VramRequirementResult requirement) {
        SpecificationVramEvaluationRequest request = new SpecificationVramEvaluationRequest(
                requirement,
                List.of(
                        spec("spec-a", "A800-80G-1", 1, 80),
                        spec("spec-b", "A800-80G-8", 8, 80)));

        return new SpecificationVramEvaluator(SpecResourceParseConfig.defaults()).evaluate(request);
    }

    /** 创建一个规格示例，resourceInfo 模拟业务 Specification.resourceInfo。 */
    private static SpecificationInput spec(String id, String model, int gpuCount, int gpuMemoryGib) {
        return new SpecificationInput(
                id,
                model,
                null,
                List.of(
                        new SpecResourceInput("gpu", BigDecimal.valueOf(gpuCount), "card", true, "GPU cards"),
                        new SpecResourceInput("gpu_memory", BigDecimal.valueOf(gpuMemoryGib), "GiB", false, "GPU memory per card")));
    }
}
