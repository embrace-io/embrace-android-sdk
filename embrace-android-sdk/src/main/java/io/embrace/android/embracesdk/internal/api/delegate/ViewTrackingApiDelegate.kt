package io.embrace.android.embracesdk.internal.api.delegate

import android.app.Application
import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb

internal class ViewTrackingApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : ViewTrackingApi {

    private val logger = bootstrapper.initModule.logger
    private val sdkClock = bootstrapper.initModule.clock
    private val featureModule by embraceImplInject(sdkCallChecker) {
        bootstrapper.featureModule
    }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }
    private val appFramework by embraceImplInject(sdkCallChecker) {
        bootstrapper.essentialServiceModule.configService.appFramework
    }

    /**
     * Variable pointing to the composeActivityListener instance obtained using reflection
     */
    private var composeActivityListenerInstance: Any? = null

    override fun registerComposeActivityListener(app: Application) {
        try {
            val composeActivityListener = Class.forName("io.embrace.android.embracesdk.compose.ComposeActivityListener")
            composeActivityListenerInstance = composeActivityListener.getDeclaredConstructor().newInstance()
            app.registerActivityLifecycleCallbacks(composeActivityListenerInstance as Application.ActivityLifecycleCallbacks?)
        } catch (e: Throwable) {
            logger.logError("registerComposeActivityListener error", e)
        }
    }

    override fun unregisterComposeActivityListener(app: Application) {
        try {
            composeActivityListenerInstance?.let {
                app.unregisterActivityLifecycleCallbacks(it as Application.ActivityLifecycleCallbacks?)
            }
        } catch (e: Throwable) {
            logger.logError("Instantiation error for ComposeActivityListener", e)
        }
    }

    override fun startView(name: String): Boolean {
        if (sdkCallChecker.check("start_view")) {
            return featureModule?.viewDataSource?.dataSource?.startView(name) ?: false
        }
        return false
    }

    override fun endView(name: String): Boolean {
        if (sdkCallChecker.check("end_view")) {
            return featureModule?.viewDataSource?.dataSource?.endView(name) ?: false
        }
        return false
    }

    override fun logTap(point: Pair<Float?, Float?>, elementName: String, type: TapBreadcrumb.TapBreadcrumbType) {
        if (sdkCallChecker.check("log_tap")) {
            featureModule?.tapDataSource?.dataSource?.logTap(point, elementName, sdkClock.now(), type)
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }

    override fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    ) {
        if (sdkCallChecker.check("log_react_native_action")) {
            featureModule?.rnActionDataSource?.dataSource?.logRnAction(name, startTime, endTime, properties, bytesSent, output)
        }
    }

    override fun logRnView(screen: String) {
        if (appFramework != AppFramework.REACT_NATIVE) {
            logger.logWarning("[Embrace] logRnView is only available on React Native", null)
            return
        }

        if (sdkCallChecker.check("log RN view")) {
            featureModule?.viewDataSource?.dataSource?.changeView(screen)
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }
}
