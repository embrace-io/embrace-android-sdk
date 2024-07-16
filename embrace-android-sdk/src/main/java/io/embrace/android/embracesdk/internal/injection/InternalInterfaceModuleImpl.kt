package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.FlutterInternalInterface
import io.embrace.android.embracesdk.ReactNativeInternalInterface
import io.embrace.android.embracesdk.UnityInternalInterface
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.EmbraceInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.FlutterInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.ReactNativeInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.UnityInternalInterfaceImpl

internal class InternalInterfaceModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    customerLogModule: CustomerLogModule,
    dataContainerModule: DataContainerModule,
    embrace: EmbraceImpl,
    crashModule: CrashModule
) : InternalInterfaceModule {

    override val embraceInternalInterface: EmbraceInternalInterface by singleton {
        EmbraceInternalInterfaceImpl(
            embrace,
            initModule,
            customerLogModule.networkCaptureService,
            dataContainerModule.eventService,
            initModule.internalErrorService,
            essentialServiceModule.configService,
            openTelemetryModule.internalTracer
        )
    }

    override val reactNativeInternalInterface: ReactNativeInternalInterface by singleton {
        ReactNativeInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            crashModule.crashService,
            essentialServiceModule.metadataService,
            essentialServiceModule.hostedSdkVersionInfo,
            initModule.logger
        )
    }

    override val unityInternalInterface: UnityInternalInterface by singleton {
        UnityInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            essentialServiceModule.hostedSdkVersionInfo,
            initModule.logger
        )
    }

    override val flutterInternalInterface: FlutterInternalInterface by singleton {
        FlutterInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            essentialServiceModule.hostedSdkVersionInfo,
            initModule.logger
        )
    }
}
