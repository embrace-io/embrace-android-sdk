package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.MomentsApi
import io.embrace.android.embracesdk.internal.event.EmbraceEventService
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.utils.PropertyUtils
import io.embrace.android.embracesdk.internal.utils.toNonNullMap

internal class MomentsApiDelegate(
    bootstrapper: ModuleInitBootstrapper,
    private val sdkCallChecker: SdkCallChecker
) : MomentsApi {

    private val logger = bootstrapper.initModule.logger
    private val eventService by embraceImplInject(sdkCallChecker) { bootstrapper.dataContainerModule.eventService }
    private val sessionOrchestrator by embraceImplInject(sdkCallChecker) { bootstrapper.sessionModule.sessionOrchestrator }

    override fun startMoment(name: String) = startMoment(name, null)
    override fun startMoment(name: String, identifier: String?) = startMoment(name, identifier, null)

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     *
     * The length of time a moment takes to execute is recorded.
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    override fun startMoment(name: String, identifier: String?, properties: Map<String, Any?>?) {
        if (sdkCallChecker.check("start_moment")) {
            eventService?.startEvent(name, identifier, PropertyUtils.normalizeProperties(properties?.toNonNullMap(), logger))
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }

    override fun endMoment(name: String) {
        endMoment(name, null, null)
    }

    override fun endMoment(name: String, identifier: String?) {
        endMoment(name, identifier, null)
    }

    override fun endMoment(name: String, properties: Map<String, Any?>?) {
        endMoment(name, null, properties)
    }

    /**
     * Signals the end of a moment with the specified name.
     *
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    override fun endMoment(name: String, identifier: String?, properties: Map<String, Any?>?) {
        if (sdkCallChecker.check("end_moment")) {
            eventService?.endEvent(name, identifier, PropertyUtils.normalizeProperties(properties?.toNonNullMap(), logger))
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }

    override fun endAppStartup() {
        endMoment(EmbraceEventService.STARTUP_EVENT_NAME, null, null)
    }

    /**
     * Signals that the app has completed startup.
     *
     * @param properties properties to include as part of the startup moment
     */
    override fun endAppStartup(properties: Map<String, Any?>) {
        endMoment(EmbraceEventService.STARTUP_EVENT_NAME, null, properties)
    }
}
