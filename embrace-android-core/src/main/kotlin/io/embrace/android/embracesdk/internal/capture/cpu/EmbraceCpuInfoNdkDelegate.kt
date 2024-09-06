package io.embrace.android.embracesdk.internal.capture.cpu

class EmbraceCpuInfoNdkDelegate : CpuInfoNdkDelegate {
    external override fun getNativeCpuName(): String
    external override fun getNativeEgl(): String
}

interface CpuInfoNdkDelegate {
    fun getNativeCpuName(): String
    fun getNativeEgl(): String
}
