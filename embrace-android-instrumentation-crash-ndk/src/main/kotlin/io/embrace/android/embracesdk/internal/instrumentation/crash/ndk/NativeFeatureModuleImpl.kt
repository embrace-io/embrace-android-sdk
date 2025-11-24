package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs

class NativeFeatureModuleImpl(
    nativeCoreModule: NativeCoreModule,
    args: InstrumentationArgs,
) : NativeFeatureModule {

    override val nativeCrashService: NativeCrashService? by lazy {
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
