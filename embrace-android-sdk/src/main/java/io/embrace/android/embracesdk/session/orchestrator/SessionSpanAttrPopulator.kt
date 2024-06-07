package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.opentelemetry.embCleanExit
import io.embrace.android.embracesdk.opentelemetry.embColdStart
import io.embrace.android.embracesdk.opentelemetry.embCrashId
import io.embrace.android.embracesdk.opentelemetry.embSdkStartupDuration
import io.embrace.android.embracesdk.opentelemetry.embSessionEndType
import io.embrace.android.embracesdk.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.opentelemetry.embSessionStartType
import io.embrace.android.embracesdk.opentelemetry.embSessionStartupDuration
import io.embrace.android.embracesdk.opentelemetry.embSessionStartupThreshold
import io.embrace.android.embracesdk.opentelemetry.embState
import io.embrace.android.embracesdk.opentelemetry.embTerminated
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionZygote
import java.util.Locale

internal class SessionSpanAttrPopulator(
    private val sessionSpanWriter: SessionSpanWriter,
    private val eventService: EventService,
    private val startupService: StartupService,
) {

    fun populateSessionSpanStartAttrs(session: SessionZygote) {
        with(sessionSpanWriter) {
            addCustomAttribute(SpanAttributeData(embColdStart.name, session.isColdStart.toString()))
            addCustomAttribute(SpanAttributeData(embSessionNumber.name, session.number.toString()))
            addCustomAttribute(SpanAttributeData(embState.name, session.appState))
            addCustomAttribute(SpanAttributeData(embCleanExit.name, false.toString()))
            session.startType.toString().toLowerCase(Locale.US).let {
                addCustomAttribute(SpanAttributeData(embSessionStartType.name, it))
            }
        }
    }

    fun populateSessionSpanEndAttrs(endType: Session.LifeEventType?, crashId: String?, coldStart: Boolean) {
        with(sessionSpanWriter) {
            addCustomAttribute(SpanAttributeData(embCleanExit.name, true.toString()))
            addCustomAttribute(SpanAttributeData(embTerminated.name, false.toString()))
            crashId?.let {
                addCustomAttribute(SpanAttributeData(embCrashId.name, crashId))
            }
            endType?.toString()?.toLowerCase(Locale.US)?.let {
                addCustomAttribute(SpanAttributeData(embSessionEndType.name, it))
            }

            val startupInfo = when {
                coldStart -> eventService.getStartupMomentInfo()
                else -> null
            }
            startupInfo?.let { info ->
                addCustomAttribute(
                    SpanAttributeData(embSdkStartupDuration.name, startupService.getSdkStartupDuration(coldStart).toString())
                )
                addCustomAttribute(SpanAttributeData(embSessionStartupDuration.name, info.duration.toString()))
                addCustomAttribute(SpanAttributeData(embSessionStartupThreshold.name, info.threshold.toString()))
            }
        }
    }
}
