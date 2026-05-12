package com.wmdoc.vram2.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/** 阶段二输出对象，包含每个规格的三种部署模式评估结果。 */
@Getter
@ToString
public final class SpecificationVramEvaluationResult {
    /** 每个规格的评估结果列表。 */
    private final List<SpecEvaluation> results;

    /** 创建阶段二输出对象，并将结果拷贝为不可变列表。 */
    public SpecificationVramEvaluationResult(List<SpecEvaluation> results) {
        this.results = List.copyOf(results);
    }

    /** 单个规格的三种部署模式评估结果。 */
    @Getter
    @ToString
    @AllArgsConstructor
    public static final class SpecEvaluation {
        /** 规格 ID。 */
        private final String specId;
        /** 规格型号。 */
        private final String specModel;
        /** 每节点 GPU 卡数。 */
        private final Double gpuCountPerNode;
        /** 单卡显存。 */
        private final Double gpuMemoryGibPerCard;
        /** 单节点总显存。 */
        private final Double nodeTotalVramGib;
        /** 单实例评估结果。 */
        private final DeploymentVramResult singleInstance;
        /** 多实例评估结果。 */
        private final DeploymentVramResult multiInstance;
        /** 集群评估结果。 */
        private final DeploymentVramResult cluster;
    }
}
