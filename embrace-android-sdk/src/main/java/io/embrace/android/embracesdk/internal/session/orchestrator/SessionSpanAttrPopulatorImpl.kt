package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.event.EventService
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.opentelemetry.embCleanExit
import io.embrace.android.embracesdk.internal.opentelemetry.embColdStart
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.opentelemetry.embErrorLogCount
import io.embrace.android.embracesdk.internal.opentelemetry.embFreeDiskBytes
import io.embrace.android.embracesdk.internal.opentelemetry.embSdkStartupDuration
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionEndType
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartType
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartupDuration
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartupThreshold
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.opentelemetry.embTerminated
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import java.util.Locale

internal class SessionSpanAttrPopulatorImpl(
    private val sessionSpanWriter: SessionSpanWriter,
    private val eventService: EventService,
    private val startupService: StartupService,
    private val logService: LogService,
    private val metadataService: MetadataService
) : SessionSpanAttrPopulator {

    override fun populateSessionSpanStartAttrs(session: SessionZygote) {
        with(sessionSpanWriter) {
            addSystemAttribute(SpanAttributeData(embColdStart.name, session.isColdStart.toString()))
            addSystemAttribute(SpanAttributeData(embSessionNumber.name, session.number.toString()))
            addSystemAttribute(SpanAttributeData(embState.name, session.appState.name.toLowerCase(Locale.US)))
            addSystemAttribute(SpanAttributeData(embCleanExit.name, false.toString()))
            addSystemAttribute(SpanAttributeData(embTerminated.name, true.toString()))

            session.startType.toString().toLowerCase(Locale.US).let {
                addSystemAttribute(SpanAttributeData(embSessionStartType.name, it))
            }
        }
    }

    override fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean) {
        with(sessionSpanWriter) {
            addSystemAttribute(SpanAttributeData(embCleanExit.name, true.toString()))
            addSystemAttribute(SpanAttributeData(embTerminated.name, false.toString()))
            crashId?.let {
                addSystemAttribute(SpanAttributeData(embCrashId.name, crashId))
            }
            endType?.toString()?.toLowerCase(Locale.US)?.let {
                addSystemAttribute(SpanAttributeData(embSessionEndType.name, it))
            }

            val startupInfo = when {
                coldStart -> eventService.getStartupMomentInfo()
                else -> null
            }
            startupInfo?.let { info ->
                addSystemAttribute(
                    SpanAttributeData(
                        embSdkStartupDuration.name,
                        startupService.getSdkStartupDuration(coldStart).toString()
                    )
                )
                addSystemAttribute(SpanAttributeData(embSessionStartupDuration.name, info.duration.toString()))
                addSystemAttribute(SpanAttributeData(embSessionStartupThreshold.name, info.threshold.toString()))
            }

            val logCount = logService.findErrorLogIds().size
            addSystemAttribute(SpanAttributeData(embErrorLogCount.name, logCount.toString()))

            metadataService.getDiskUsage()?.deviceDiskFree?.let { free ->
                addSystemAttribute(SpanAttributeData(embFreeDiskBytes.name, free.toString()))
            }
        }
    }
}
