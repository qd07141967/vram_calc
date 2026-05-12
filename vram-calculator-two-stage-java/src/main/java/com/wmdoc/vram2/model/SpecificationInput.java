package com.wmdoc.vram2.model;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

/** 规格输入对象，对应业务中的 Specification。 */
@Getter
@ToString
public final class SpecificationInput {
    /** 规格 ID。 */
    private final String id;
    /** 规格型号。 */
    private final String model;
    /** 规格描述。 */
    private final String desc;
    /** 规格资源明细。 */
    private final List<SpecResourceInput> resourceInfo;

    /** 创建规格输入对象，并将资源明细拷贝为不可变列表。 */
    public SpecificationInput(String id, String model, String desc, List<SpecResourceInput> resourceInfo) {
        this.id = id;
        this.model = model;
        this.desc = desc;
        this.resourceInfo = List.copyOf(resourceInfo);
    }
}
