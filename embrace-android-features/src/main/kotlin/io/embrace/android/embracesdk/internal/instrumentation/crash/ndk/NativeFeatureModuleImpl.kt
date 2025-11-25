package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.injection.singleton

class NativeFeatureModuleImpl(
    nativeCoreModule: NativeCoreModule,
    args: InstrumentationArgs,
) : NativeFeatureModule {

    override val nativeCrashService: NativeCrashService? by singleton {
        if (!args.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            null
        } else {
            NativeCrashDataSourceImpl(
                nativeCrashProcessor = nativeCoreModule.processor,
                args = args,
            )
        }
    }
}
