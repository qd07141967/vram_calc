package com.wmdoc.vram2.calculator;

import com.wmdoc.vram2.config.SpecResourceParseConfig;
import com.wmdoc.vram2.model.DeploymentVramResult;
import com.wmdoc.vram2.model.SpecResourceInput;
import com.wmdoc.vram2.model.SpecificationInput;
import com.wmdoc.vram2.model.SpecificationVramEvaluationRequest;
import com.wmdoc.vram2.model.SpecificationVramEvaluationResult;
import com.wmdoc.vram2.model.VramRequirementResult;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 阶段二评估器：对规格列表一次性输出三种部署模式的显存消耗结果。 */
@RequiredArgsConstructor
public final class SpecificationVramEvaluator {
    /** 规格资源解析配置。 */
    private final SpecResourceParseConfig parseConfig;

    /** 对规格列表进行显存消耗评估。 */
    public SpecificationVramEvaluationResult evaluate(SpecificationVramEvaluationRequest request) {
        validateRequirement(request.getVramRequirement());

        List<SpecificationVramEvaluationResult.SpecEvaluation> results = new ArrayList<>();
        for (SpecificationInput specification : request.getSpecifications()) {
            results.add(evaluateOneSpec(request.getVramRequirement(), specification));
        }
        return new SpecificationVramEvaluationResult(results);
    }

    /** 评估单个规格在三种部署模式下的显存消耗。 */
    private SpecificationVramEvaluationResult.SpecEvaluation evaluateOneSpec(
            VramRequirementResult requirement,
            SpecificationInput specification) {
        ParsedSpec parsed = parseSpecification(specification);
        if (!parsed.calculable) {
            DeploymentVramResult failed = DeploymentVramResult.notCalculable(parsed.reason);
            return new SpecificationVramEvaluationResult.SpecEvaluation(
                    specification.getId(),
                    specification.getModel(),
                    parsed.gpuCountPerNode,
                    parsed.gpuMemoryGibPerCard,
                    parsed.nodeTotalVramGib,
                    failed,
                    failed,
                    failed);
        }

        return new SpecificationVramEvaluationResult.SpecEvaluation(
                specification.getId(),
                specification.getModel(),
                parsed.gpuCountPerNode,
                parsed.gpuMemoryGibPerCard,
                parsed.nodeTotalVramGib,
                evaluateSingleInstance(requirement, parsed.nodeTotalVramGib),
                evaluateMultiInstance(requirement, parsed.nodeTotalVramGib),
                evaluateCluster(requirement, parsed.nodeTotalVramGib));
    }

    /** 解析规格中的 GPU 卡数、单卡显存和单节点总显存。 */
    private ParsedSpec parseSpecification(SpecificationInput specification) {
        Double gpuCount = null;
        Double gpuMemory = null;

        for (SpecResourceInput resource : specification.getResourceInfo()) {
            if (resource.getNum() == null) {
                continue;
            }
            if (gpuCount == null && isGpuCount(resource)) {
                gpuCount = resource.getNum().doubleValue();
            }
            if (gpuMemory == null && parseConfig.isGpuMemoryType(resource.getType())) {
                gpuMemory = toGib(resource.getNum(), resource.getUnit());
            }
        }

        if (gpuCount == null) {
            return ParsedSpec.failed("missing_gpu_count_per_node", null, gpuMemory);
        }
        if (gpuMemory == null) {
            return ParsedSpec.failed("missing_gpu_memory_gib_per_card", gpuCount, null);
        }
        if (gpuCount <= 0) {
            return ParsedSpec.failed("invalid_gpu_count_per_node", gpuCount, gpuMemory);
        }
        if (gpuMemory <= 0) {
            return ParsedSpec.failed("invalid_gpu_memory_gib_per_card", gpuCount, gpuMemory);
        }
        return ParsedSpec.success(gpuCount, gpuMemory, gpuCount * gpuMemory);
    }

    /** 判断资源是否表示 GPU 卡数。 */
    private boolean isGpuCount(SpecResourceInput resource) {
        if (parseConfig.isGpuCountType(resource.getType())) {
            return true;
        }
        return Boolean.TRUE.equals(resource.getGpuType()) && "card".equals(resource.getUnit());
    }

    /** 将资源数值转换为 GiB；GB 在当前估算中按 GiB 处理。 */
    private double toGib(BigDecimal value, String unit) {
        if (unit == null || unit.isBlank() || "gib".equals(unit) || "gb".equals(unit)) {
            return value.doubleValue();
        }
        if ("mib".equals(unit) || "mb".equals(unit)) {
            return value.doubleValue() / 1024.0;
        }
        throw new IllegalArgumentException("Unsupported gpu memory unit: " + unit);
    }

    /** 计算单实例结果：固定使用一个节点，显存需求必须小于等于单节点总显存。 */
    private DeploymentVramResult evaluateSingleInstance(VramRequirementResult requirement, double nodeTotalVramGib) {
        double required = requirement.getRequiredVramGib();
        if (required > nodeTotalVramGib) {
            return new DeploymentVramResult(
                    1,
                    required,
                    nodeTotalVramGib,
                    required,
                    null,
                    null,
                    false,
                    "insufficient_single_node_vram");
        }
        return deploymentResult(1, required, nodeTotalVramGib, required);
    }

    /**
     * 计算多实例结果。
     *
     * <p>多实例没有 RDMA，因此每个节点都要加载一份模型权重和框架预留；KV Cache 可以按节点分摊。
     * 整体预留不能直接整份复制，因为阶段一的 overallReserveGib 已经包含 KV Cache 的预留。
     * 所以这里用阶段一返回的 overallReserveRatio 分别对“复制部分”和“分摊部分”重新计算预留。</p>
     */
    private DeploymentVramResult evaluateMultiInstance(VramRequirementResult requirement, double nodeTotalVramGib) {
        // 1. 整体预留比例：来自阶段一的 overall_reserve_ratio，例如 0.10 表示额外预留 10%。
        double reserveRatio = requirement.getOverallReserveRatio();

        // 2. 每节点必须复制的基础显存：模型权重 + 框架预留，不包含 KV Cache。
        double replicatedBase = requirement.getWeightMemoryGib() + requirement.getFrameworkReserveGib();

        // 3. 每节点复制部分的实际占用：复制基础显存 + 复制基础显存对应的整体预留。
        double replicatedPerNode = applyReserve(replicatedBase, reserveRatio);

        // 4. 可分摊的 KV 总显存：KV Cache + KV Cache 对应的整体预留。
        double kvWithReserve = applyReserve(requirement.getKvCacheGib(), reserveRatio);

        // 5. 每节点可用于承载 KV 分片的剩余容量。
        double capacityForKvPerNode = nodeTotalVramGib - replicatedPerNode;

        // 6. 如果复制部分已经超过单节点容量，则多实例也无法承载。
        if (capacityForKvPerNode < 0 || (capacityForKvPerNode == 0 && kvWithReserve > 0)) {
            return DeploymentVramResult.notCalculable("insufficient_multi_instance_base_vram");
        }

        // 7. 节点数 = KV 总显存按每节点剩余容量分摊后向上取整；没有 KV 时至少需要 1 个节点。
        int requiredNodes = kvWithReserve <= 0 ? 1 : (int) Math.ceil(kvWithReserve / capacityForKvPerNode);
        requiredNodes = Math.max(requiredNodes, 1);

        // 8. 每节点实际显存占用 = 每节点复制部分 + 当前节点承载的 KV 分片。
        double vramPerNode = replicatedPerNode + kvWithReserve / requiredNodes;

        // 9. 消耗总显存容量 = 节点数 x 单节点总显存，用于计算资源池占用。
        double consumedTotal = requiredNodes * nodeTotalVramGib;

        // 10. 实际使用总显存 = 每节点复制部分 x 节点数 + 分摊后的 KV 总显存。
        double actualUsed = requiredNodes * replicatedPerNode + kvWithReserve;

        // 11. 统一生成结果，并在 deploymentResult 中计算显存占用比例和百分比。
        return deploymentResult(requiredNodes, vramPerNode, consumedTotal, actualUsed);
    }

    /** 对某一段显存应用整体预留比例，用于阶段二按部署方式重新计算预留。 */
    private double applyReserve(double vramGib, double reserveRatio) {
        return vramGib * (1 + reserveRatio);
    }

    /** 计算集群结果：多个节点作为整体资源池承载 requiredVramGib。 */
    private DeploymentVramResult evaluateCluster(VramRequirementResult requirement, double nodeTotalVramGib) {
        int requiredNodes = (int) Math.ceil(requirement.getRequiredVramGib() / nodeTotalVramGib);
        requiredNodes = Math.max(requiredNodes, 1);
        double consumedTotal = requiredNodes * nodeTotalVramGib;
        return deploymentResult(requiredNodes, null, consumedTotal, requirement.getRequiredVramGib());
    }

    /** 创建可计算的部署结果，并统一计算占用比例和百分比。 */
    private DeploymentVramResult deploymentResult(
            int requiredNodes,
            Double vramPerNode,
            double consumedTotalVramGib,
            double actualUsedVramGib) {
        double ratio = actualUsedVramGib / consumedTotalVramGib;
        return new DeploymentVramResult(
                requiredNodes,
                vramPerNode,
                consumedTotalVramGib,
                actualUsedVramGib,
                ratio,
                ratio * 100.0,
                true,
                null);
    }

    /** 校验阶段一显存需求是否有效。 */
    private void validateRequirement(VramRequirementResult requirement) {
        if (requirement.getRequiredVramGib() <= 0) {
            throw new IllegalArgumentException("invalid_required_vram_gib");
        }
    }

    /** 规格解析中间结果。 */
    private static final class ParsedSpec {
        /** 每节点 GPU 卡数。 */
        private final Double gpuCountPerNode;
        /** 单卡显存，单位 GiB。 */
        private final Double gpuMemoryGibPerCard;
        /** 单节点总显存，单位 GiB。 */
        private final Double nodeTotalVramGib;
        /** 是否可计算。 */
        private final boolean calculable;
        /** 不可计算原因。 */
        private final String reason;

        /** 创建规格解析中间结果。 */
        private ParsedSpec(Double gpuCountPerNode, Double gpuMemoryGibPerCard, Double nodeTotalVramGib, boolean calculable, String reason) {
            this.gpuCountPerNode = gpuCountPerNode;
            this.gpuMemoryGibPerCard = gpuMemoryGibPerCard;
            this.nodeTotalVramGib = nodeTotalVramGib;
            this.calculable = calculable;
            this.reason = reason;
        }

        /** 创建可计算解析结果。 */
        private static ParsedSpec success(double gpuCountPerNode, double gpuMemoryGibPerCard, double nodeTotalVramGib) {
            return new ParsedSpec(gpuCountPerNode, gpuMemoryGibPerCard, nodeTotalVramGib, true, null);
        }

        /** 创建不可计算解析结果。 */
        private static ParsedSpec failed(String reason, Double gpuCountPerNode, Double gpuMemoryGibPerCard) {
            return new ParsedSpec(gpuCountPerNode, gpuMemoryGibPerCard, null, false, reason);
        }
    }
}
