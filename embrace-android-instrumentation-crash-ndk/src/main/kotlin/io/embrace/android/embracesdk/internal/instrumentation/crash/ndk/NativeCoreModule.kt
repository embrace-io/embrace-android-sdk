package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate

interface NativeCoreModule {
    val sharedObjectLoader: SharedObjectLoader
    val processor: NativeCrashProcessor
    val delegate: JniDelegate
    val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller?
    val nativeCrashService: NativeCrashService?
}
