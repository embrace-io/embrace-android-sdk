package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.singleton

internal class NativeFeatureModuleImpl(
    nativeCoreModule: NativeCoreModule,
    instrumentationModule: InstrumentationModule,
) : NativeFeatureModule {

    private val args by singleton { instrumentationModule.instrumentationArgs }

    override val nativeCrashService: NativeCrashService? by singleton {
        if (!args.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            null
        } else {
            NativeCrashDataSourceImpl(
                nativeCrashProcessor = nativeCoreModule.processor,
                ordinalStore = args.ordinalStore,
                args = args,
                serializer = args.serializer,
            )
        }
    }
}
