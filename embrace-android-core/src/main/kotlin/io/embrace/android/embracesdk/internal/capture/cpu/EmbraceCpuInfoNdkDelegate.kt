package io.embrace.android.embracesdk.internal.capture.cpu

public class EmbraceCpuInfoNdkDelegate : CpuInfoNdkDelegate {
    external override fun getNativeCpuName(): String
    external override fun getNativeEgl(): String
}

public interface CpuInfoNdkDelegate {
    public fun getNativeCpuName(): String
    public fun getNativeEgl(): String
}
