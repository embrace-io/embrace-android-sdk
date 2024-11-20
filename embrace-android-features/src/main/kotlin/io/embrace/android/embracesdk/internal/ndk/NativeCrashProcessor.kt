package io.embrace.android.embracesdk.internal.ndk

/**
 * Processes any native crashes that are stored on disk from previous processes & converts them
 * into a format that can be ingested by the delivery layer.
 */
interface NativeCrashProcessor {
    fun processNativeCrashes()
}
