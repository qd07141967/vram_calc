package com.wmdoc.vram2.model;

import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Objects;

/** 阶段二输入对象，用显存需求明细和规格列表一次性评估三种部署模式。 */
@Getter
@ToString
public final class SpecificationVramEvaluationRequest {
    /** 阶段一输出的显存需求明细。 */
    private final VramRequirementResult vramRequirement;
    /** 待评估的规格列表。 */
    private final List<SpecificationInput> specifications;

    /** 创建阶段二规格显存评估请求。 */
    public SpecificationVramEvaluationRequest(VramRequirementResult vramRequirement, List<SpecificationInput> specifications) {
        this.vramRequirement = Objects.requireNonNull(vramRequirement, "vramRequirement");
        this.specifications = List.copyOf(specifications);
    }
}
