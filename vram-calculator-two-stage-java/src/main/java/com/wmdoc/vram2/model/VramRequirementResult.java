package com.wmdoc.vram2.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/** 阶段一输出对象，包含显存需求的各个组成项。 */
@Getter
@ToString
@AllArgsConstructor
public final class VramRequirementResult {
    /** 模型权重显存。 */
    private final double weightMemoryGib;
    /** 框架预留显存。 */
    private final double frameworkReserveGib;
    /** KV Cache 或状态显存。 */
    private final double kvCacheGib;
    /** 基础显存，等于权重 + 框架预留 + KV/状态显存。 */
    private final double baseVramGib;
    /** 整体额外预留显存。 */
    private final double overallReserveGib;
    /** 整体额外预留比例，用于阶段二按部署方式重新分摊预留。 */
    private final double overallReserveRatio;
    /** 最终所需显存。 */
    private final double requiredVramGib;
    /** KV/状态显存使用的公式说明。 */
    private final String kvFormula;
}
