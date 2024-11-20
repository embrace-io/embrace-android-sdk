package io.embrace.android.embracesdk.internal.ndk

/**
 * Installs signal handlers that capture C/C++ crashes.
 */
interface NativeCrashHandlerInstaller {
    fun install()
}
