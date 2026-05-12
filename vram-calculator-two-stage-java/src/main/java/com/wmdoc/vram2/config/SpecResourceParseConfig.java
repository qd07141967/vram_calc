package com.wmdoc.vram2.config;

import lombok.Getter;
import lombok.ToString;

import java.util.Set;

/** 规格资源解析配置，用于从 Specification.resourceInfo 中识别 GPU 卡数和单卡显存。 */
@Getter
@ToString
public final class SpecResourceParseConfig {
    /** 可识别为 GPU 卡数的资源类型。 */
    private final Set<String> gpuCountTypes;
    /** 可识别为 GPU 显存的资源类型。 */
    private final Set<String> gpuMemoryTypes;

    /** 创建规格资源解析配置，并拷贝为不可变集合。 */
    public SpecResourceParseConfig(Set<String> gpuCountTypes, Set<String> gpuMemoryTypes) {
        this.gpuCountTypes = Set.copyOf(gpuCountTypes);
        this.gpuMemoryTypes = Set.copyOf(gpuMemoryTypes);
    }

    /** 返回默认解析配置。 */
    public static SpecResourceParseConfig defaults() {
        return new SpecResourceParseConfig(
                Set.of("gpu", "gpu_count", "card"),
                Set.of("gpu_memory", "gpu_memory_gib", "vram"));
    }

    /** 判断资源类型是否表示 GPU 卡数。 */
    public boolean isGpuCountType(String type) {
        return type != null && gpuCountTypes.contains(type);
    }

    /** 判断资源类型是否表示 GPU 显存。 */
    public boolean isGpuMemoryType(String type) {
        return type != null && gpuMemoryTypes.contains(type);
    }
}
