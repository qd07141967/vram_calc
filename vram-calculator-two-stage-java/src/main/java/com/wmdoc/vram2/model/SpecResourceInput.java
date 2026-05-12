package com.wmdoc.vram2.model;

import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

/** 规格资源明细输入，对应 Specification.resourceInfo 中的单条资源。 */
@Getter
@ToString
public final class SpecResourceInput {
    /** 资源类型，例如 gpu、gpu_memory。 */
    private final String type;
    /** 资源数值。 */
    private final BigDecimal num;
    /** 资源单位，例如 card、GiB。 */
    private final String unit;
    /** 是否 GPU 类型指标。 */
    private final Boolean gpuType;
    /** 资源描述。 */
    private final String desc;

    /** 创建规格资源明细，并标准化 type/unit 以便配置匹配。 */
    public SpecResourceInput(String type, BigDecimal num, String unit, Boolean gpuType, String desc) {
        this.type = normalize(type);
        this.num = num;
        this.unit = normalize(unit);
        this.gpuType = gpuType;
        this.desc = desc;
    }

    /** 标准化字符串，空值保留为 null。 */
    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}
