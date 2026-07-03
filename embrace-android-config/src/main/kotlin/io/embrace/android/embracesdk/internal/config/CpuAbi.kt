package io.embrace.android.embracesdk.internal.config

enum class CpuAbi(val archName: String) {

    ARMEABI_V7A("armeabi-v7a"),
    ARM64_V8A("arm64-v8a"),
    X86("x86"),
    X86_64("x86_64"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromArchName(abi: String): CpuAbi {
            return CpuAbi.entries.find { it.archName == abi } ?: UNKNOWN
        }
    }
}
