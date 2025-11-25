package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService

interface NativeCoreModule {
    val sharedObjectLoader: SharedObjectLoader
    val processor: NativeCrashProcessor
    val symbolService: SymbolService
    val delegate: JniDelegate
    val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller?
    val nativeCrashService: NativeCrashService?
}
