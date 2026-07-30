package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.EmbraceInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.FlutterInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.ReactNativeInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.UnityInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.config.ConfigService

internal class InternalInterfaceModuleImpl(
    initModule: InitModule,
    configService: ConfigService,
    payloadSourceModule: PayloadSourceModule,
    embrace: EmbraceImpl,
    bootstrapper: ModuleInitBootstrapper,
) : InternalInterfaceModule {

    override val embraceInternalInterface: EmbraceInternalInterface by lazy {
        EmbraceInternalInterfaceImpl(
            configService,
            payloadSourceModule.resourceSource,
        )
    }

    override val reactNativeInternalInterface: ReactNativeInternalInterface by lazy {
        ReactNativeInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            bootstrapper,
            payloadSourceModule.rnBundleIdTracker,
            payloadSourceModule.hostedSdkVersionInfo,
            initModule.logger,
        )
    }

    override val unityInternalInterface: UnityInternalInterface by lazy {
        UnityInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            payloadSourceModule.hostedSdkVersionInfo,
            initModule.logger,
        )
    }

    override val flutterInternalInterface: FlutterInternalInterface by lazy {
        FlutterInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            payloadSourceModule.hostedSdkVersionInfo,
            initModule.logger,
        )
    }
}
