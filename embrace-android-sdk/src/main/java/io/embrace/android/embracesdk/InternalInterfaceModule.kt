package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.CrashModule
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.singleton

internal interface InternalInterfaceModule {
    val embraceInternalInterface: EmbraceInternalInterface
    val reactNativeInternalInterface: ReactNativeInternalInterface
    val unityInternalInterface: UnityInternalInterface
    val flutterInternalInterface: FlutterInternalInterface
}

internal class InternalInterfaceModuleImpl(
    coreModule: CoreModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    embrace: EmbraceImpl,
    crashModule: CrashModule
) : InternalInterfaceModule {

    override val embraceInternalInterface: EmbraceInternalInterface by singleton {
        EmbraceInternalInterfaceImpl(embrace)
    }

    override val reactNativeInternalInterface: ReactNativeInternalInterface by singleton {
        ReactNativeInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            coreModule.appFramework,
            androidServicesModule.preferencesService,
            crashModule.crashService,
            essentialServiceModule.metadataService,
            coreModule.logger
        )
    }

    override val unityInternalInterface: UnityInternalInterface by singleton {
        UnityInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            androidServicesModule.preferencesService,
            coreModule.logger
        )
    }

    override val flutterInternalInterface: FlutterInternalInterface by singleton {
        FlutterInternalInterfaceImpl(
            embrace,
            embraceInternalInterface,
            essentialServiceModule.metadataService,
            coreModule.logger
        )
    }
}
