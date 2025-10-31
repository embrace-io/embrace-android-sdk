package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.ndk.NativeCrashDataSourceImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService

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
                preferencesService = androidServicesModule.preferencesService,
                args = instrumentationModule.instrumentationArgs,
                serializer = initModule.jsonSerializer,
            )
        }
    }
}
