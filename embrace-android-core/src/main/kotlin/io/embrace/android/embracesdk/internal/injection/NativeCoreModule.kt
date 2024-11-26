package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.ndk.NativeCrashProcessor
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService

interface NativeCoreModule {
    val sharedObjectLoader: SharedObjectLoader
    val processor: NativeCrashProcessor
    val symbolService: SymbolService
    val delegate: JniDelegate
}
