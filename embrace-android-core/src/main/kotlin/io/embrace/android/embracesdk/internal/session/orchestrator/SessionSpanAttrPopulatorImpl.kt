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
            addSessionAttribute(SpanAttributeData(embColdStart.name, session.isColdStart.toString()))
            addSessionAttribute(SpanAttributeData(embSessionNumber.name, session.number.toString()))
            addSessionAttribute(SpanAttributeData(embState.name, session.appState.name.lowercase(Locale.US)))
            addSessionAttribute(SpanAttributeData(embCleanExit.name, false.toString()))
            addSessionAttribute(SpanAttributeData(embTerminated.name, true.toString()))

            session.startType.toString().lowercase(Locale.US).let {
                addSessionAttribute(SpanAttributeData(embSessionStartType.name, it))
            }
        }
    }

    override fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean) {
        with(sessionSpanWriter) {
            addSessionAttribute(SpanAttributeData(embCleanExit.name, true.toString()))
            addSessionAttribute(SpanAttributeData(embTerminated.name, false.toString()))
            crashId?.let {
                addSessionAttribute(SpanAttributeData(embCrashId.name, crashId))
            }
            endType?.toString()?.lowercase(Locale.US)?.let {
                addSessionAttribute(SpanAttributeData(embSessionEndType.name, it))
            }
            if (coldStart) {
                startupService.getSdkStartupDuration()?.let { duration ->
                    addSessionAttribute(SpanAttributeData(embSessionStartupDuration.name, duration.toString()))
                }
            }

            val logCount = logService.getErrorLogsCount()
            addSessionAttribute(SpanAttributeData(embErrorLogCount.name, logCount.toString()))

            metadataService.getDiskUsage()?.deviceDiskFree?.let { free ->
                addSessionAttribute(SpanAttributeData(embFreeDiskBytes.name, free.toString()))
            }
        }
    }
}
