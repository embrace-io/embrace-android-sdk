package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.ConfigModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.singleton

internal class NativeFeatureModuleImpl(
    initModule: InitModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    nativeCoreModule: NativeCoreModule,
    instrumentationModule: InstrumentationModule,
) : NativeFeatureModule {

    override val nativeCrashService: NativeCrashService? by singleton {
        if (!configModule.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            null
        } else {
            NativeCrashDataSourceImpl(
                nativeCrashProcessor = nativeCoreModule.processor,
                ordinalStore = androidServicesModule.ordinalStore,
                args = instrumentationModule.instrumentationArgs,
                serializer = initModule.jsonSerializer,
            )
        }
    }
}
