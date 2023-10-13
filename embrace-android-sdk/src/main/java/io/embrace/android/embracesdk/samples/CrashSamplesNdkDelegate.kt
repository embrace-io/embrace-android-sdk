package io.embrace.android.embracesdk.samples

internal interface CrashSamplesNdkDelegate {
    fun sigIllegalInstruction()
    fun throwException()
    fun sigAbort()
    fun sigfpe()
    fun sigsegv()
}
