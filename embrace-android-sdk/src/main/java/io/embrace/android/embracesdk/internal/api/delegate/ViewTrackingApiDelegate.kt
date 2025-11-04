package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.instrumentation.view.ViewDataSource
import io.embrace.android.embracesdk.internal.payload.AppFramework

internal class ViewTrackingApiDelegate(
    private val bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker,
) : ViewTrackingApi {

    private val featureModule by embraceImplInject(sdkCallChecker) {
        bootstrapper.featureModule
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) {
        bootstrapper.sessionOrchestrationModule.sessionOrchestrator
    }
    private val appFramework by embraceImplInject(sdkCallChecker) {
        bootstrapper.configModule.configService.appFramework
    }

    override fun startView(name: String): Boolean {
        if (sdkCallChecker.check("start_view")) {
            val dataSource = bootstrapper.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
            return dataSource?.startView(name) ?: false
        }
        return false
    }

    override fun endView(name: String): Boolean {
        if (sdkCallChecker.check("end_view")) {
            val dataSource = bootstrapper.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
            return dataSource?.endView(name) ?: false
        }
        return false
    }

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String,
    ) {
        if (sdkCallChecker.check("log_react_native_action")) {
            featureModule?.rnActionDataSource?.dataSource?.logRnAction(
                name,
                startTime,
                endTime,
                properties,
                bytesSent,
                output
            )
        }
    }

    override fun logRnView(screen: String) {
        if (appFramework != AppFramework.REACT_NATIVE) {
            return
        }

        if (sdkCallChecker.check("log RN view")) {
            val dataSource = bootstrapper.instrumentationModule.instrumentationRegistry.findByType(ViewDataSource::class)
            dataSource?.changeView(screen)
        }
    }
}
