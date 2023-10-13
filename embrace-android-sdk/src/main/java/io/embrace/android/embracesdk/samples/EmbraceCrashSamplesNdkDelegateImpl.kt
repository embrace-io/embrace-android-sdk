package io.embrace.android.embracesdk.samples

internal class EmbraceCrashSamplesNdkDelegateImpl : CrashSamplesNdkDelegate {
    external override fun sigIllegalInstruction()
    external override fun throwException()
    external override fun sigAbort()
    external override fun sigfpe()
    external override fun sigsegv()
}
