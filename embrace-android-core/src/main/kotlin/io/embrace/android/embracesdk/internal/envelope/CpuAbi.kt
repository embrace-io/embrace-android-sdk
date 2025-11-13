package io.embrace.android.embracesdk.internal.envelope

import android.os.Build

enum class CpuAbi(
    val archName: String,
    val is32BitDevice: Boolean,
) {

    ARMEABI_V7A("armeabi-v7a", true),
    ARM64_V8A("arm64-v8a", false),
    X86("x86", true),
    X86_64("x86_64", false),
    UNKNOWN("unknown", false);

    companion object {
        fun current() = fromArchName(Build.SUPPORTED_ABIS[0])

        fun fromArchName(abi: String): CpuAbi {
            return CpuAbi.entries.find { it.archName == abi } ?: UNKNOWN
        }
    }
}
