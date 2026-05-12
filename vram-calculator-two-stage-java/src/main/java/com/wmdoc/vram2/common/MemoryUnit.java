package com.wmdoc.vram2.common;

/** 显存单位转换工具，统一使用二进制 GiB。 */
public final class MemoryUnit {
    /** 1 GiB 对应的字节数。 */
    public static final double GIB = 1024.0 * 1024.0 * 1024.0;

    /** 工具类不允许实例化。 */
    private MemoryUnit() {
    }

    /** 将字节数转换为 GiB。 */
    public static double bytesToGiB(double bytes) {
        return bytes / GIB;
    }

    /** 将 GiB 转换为字节数。 */
    public static double gibToBytes(double gib) {
        return gib * GIB;
    }
}
