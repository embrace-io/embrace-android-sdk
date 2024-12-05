package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.opentelemetry.embCleanExit
import io.embrace.android.embracesdk.internal.opentelemetry.embColdStart
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.opentelemetry.embErrorLogCount
import io.embrace.android.embracesdk.internal.opentelemetry.embFreeDiskBytes
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionEndType
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartType
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartupDuration
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.opentelemetry.embTerminated
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionZygote
import java.util.Locale

internal class SessionSpanAttrPopulatorImpl(
    private val sessionSpanWriter: SessionSpanWriter,
    private val startupService: StartupService,
    private val logService: LogService,
    private val metadataService: MetadataService,
) : SessionSpanAttrPopulator {

    override fun populateSessionSpanStartAttrs(session: SessionZygote) {
        with(sessionSpanWriter) {
            addAttribute(SpanAttributeData(embColdStart.name, session.isColdStart.toString()))
            addAttribute(SpanAttributeData(embSessionNumber.name, session.number.toString()))
            addAttribute(SpanAttributeData(embState.name, session.appState.name.lowercase(Locale.US)))
            addAttribute(SpanAttributeData(embCleanExit.name, false.toString()))
            addAttribute(SpanAttributeData(embTerminated.name, true.toString()))

            session.startType.toString().lowercase(Locale.US).let {
                addAttribute(SpanAttributeData(embSessionStartType.name, it))
            }
        }
    }

    override fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean) {
        with(sessionSpanWriter) {
            addAttribute(SpanAttributeData(embCleanExit.name, true.toString()))
            addAttribute(SpanAttributeData(embTerminated.name, false.toString()))
            crashId?.let {
                addAttribute(SpanAttributeData(embCrashId.name, crashId))
            }
            endType?.toString()?.lowercase(Locale.US)?.let {
                addAttribute(SpanAttributeData(embSessionEndType.name, it))
            }
            if (coldStart) {
                startupService.getSdkStartupDuration()?.let { duration ->
                    addAttribute(SpanAttributeData(embSessionStartupDuration.name, duration.toString()))
                }
            }

            val logCount = logService.getErrorLogsCount()
            addAttribute(SpanAttributeData(embErrorLogCount.name, logCount.toString()))

            metadataService.getDiskUsage()?.deviceDiskFree?.let { free ->
                addAttribute(SpanAttributeData(embFreeDiskBytes.name, free.toString()))
            }
        }
    }
}
