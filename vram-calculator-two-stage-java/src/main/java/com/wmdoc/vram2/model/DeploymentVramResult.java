package com.wmdoc.vram2.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/** 单个部署模式下的规格显存评估结果。 */
@Getter
@ToString
@AllArgsConstructor
public final class DeploymentVramResult {
    /** 需要的节点数量。 */
    private final int requiredNodes;
    /** 每节点实际显存占用，多实例下特别有意义。 */
    private final Double vramPerNodeGib;
    /** 按节点粒度占用的总显存容量。 */
    private final double consumedTotalVramGib;
    /** 模型实际消耗的总显存。 */
    private final double actualUsedVramGib;
    /** 实际消耗显存占节点总容量的比例。 */
    private final Double vramUsageRatio;
    /** 实际消耗显存占节点总容量的百分比。 */
    private final Double vramUsagePercent;
    /** 该部署模式在当前规格下是否可计算或可承载。 */
    private final boolean calculable;
    /** 不可计算或不可承载原因。 */
    private final String reason;

    /** 创建不可计算结果。 */
    public static DeploymentVramResult notCalculable(String reason) {
        return new DeploymentVramResult(0, null, 0, 0, null, null, false, reason);
    }
}
