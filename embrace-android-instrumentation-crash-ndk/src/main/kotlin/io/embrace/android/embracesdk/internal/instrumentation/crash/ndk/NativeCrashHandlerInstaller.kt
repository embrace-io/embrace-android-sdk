package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

/**
 * Installs signal handlers that capture C/C++ crashes.
 */
internal interface NativeCrashHandlerInstaller {
    fun install()
}
