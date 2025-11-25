package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.StorageModule
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [NativeCoreModule]. Matches the signature of the constructor for [NativeCoreModuleImpl]
 */
typealias NativeCoreModuleSupplier = (
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    instrumentationArgs: InstrumentationArgs,
    delegateProvider: Provider<JniDelegate?>,
    sharedObjectLoaderProvider: Provider<SharedObjectLoader?>,
    symbolServiceProvider: Provider<SymbolService?>,
) -> NativeCoreModule
