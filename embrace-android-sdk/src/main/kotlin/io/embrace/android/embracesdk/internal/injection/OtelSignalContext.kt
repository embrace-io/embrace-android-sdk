package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import io.opentelemetry.kotlin.semconv.UserAttributes

/**
 * Supplies the session and user context that is stamped on OTel signals.
 *
 * The modules that hold this context are constructed after the OTel module, so they are supplied as forward
 * references. Signals recorded before [markReady] carry no context.
 */
internal class OtelSignalContext(
    private val essentialServiceModuleProvider: Provider<EssentialServiceModule>,
    private val userSessionOrchestrationModuleProvider: Provider<UserSessionOrchestrationModule>,
    private val instrumentationModuleProvider: Provider<InstrumentationModule>,
) {

    @Volatile
    private var ready: Boolean = false

    /**
     * Called once every module that supplies context has been constructed.
     */
    fun markReady() {
        ready = true
    }

    fun getSessionIdsProvider(): SessionIdsProvider? = if (ready) {
        userSessionOrchestrationModuleProvider().sessionIdsProvider
    } else {
        null
    }

    fun getUserId(): String? = if (ready) {
        essentialServiceModuleProvider().userService.getUserInfo().userId
    } else {
        null
    }

    /**
     * A snapshot of the SDK state that is stamped on OTel log records at the time they are emitted.
     */
    @OptIn(ExperimentalSemconv::class)
    fun getEventMetadata(): Map<String, String> {
        if (!ready) {
            return emptyMap()
        }
        val essentialServiceModule = essentialServiceModuleProvider()
        return mutableMapOf<String, String>().apply {
            val sessionPart = essentialServiceModule.sessionPartTracker.getActiveSessionPart()
            val sessionState = sessionPart?.processState ?: essentialServiceModule.processStateTracker.getAppState()
            val sessionIds = userSessionOrchestrationModuleProvider().sessionIdsProvider.getActiveSessionIds()

            put(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionIds.sessionPartId)
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, sessionIds.userSessionId)
            put(EmbSessionAttributes.EMB_STATE, sessionState.description)
            essentialServiceModule.userService.getUserInfo().userId?.let {
                put(UserAttributes.USER_ID, it)
            }
            essentialServiceModule.experimentTrackingService.getRecords()?.let {
                put(EmbCommonAttributes.EMB_EXPERIMENTS, it)
            }
            putAll(
                essentialServiceModule.userSessionPropertiesService
                    .getProperties()
                    .mapKeys { property ->
                        property.key.toEmbraceAttributeName()
                    },
            )
            instrumentationModuleProvider().instrumentationRegistry.getCurrentStates().forEach {
                put(it.key, it.value.toString())
            }
        }
    }
}
