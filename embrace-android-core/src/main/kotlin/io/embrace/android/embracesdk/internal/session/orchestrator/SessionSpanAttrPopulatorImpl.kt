package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionToken
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import java.util.Locale

internal class SessionSpanAttrPopulatorImpl(
    private val destination: TelemetryDestination,
    private val startupDurationProvider: () -> Long?,
    private val logLimitingService: LogLimitingService,
    private val metadataService: MetadataService,
) : SessionSpanAttrPopulator {

    override fun populateSessionSpanStartAttrs(session: SessionToken) {
        with(destination) {
            addSessionAttribute(EmbSessionAttributes.EMB_COLD_START, session.isColdStart.toString())
            addSessionAttribute(EmbSessionAttributes.EMB_SESSION_NUMBER, session.number.toString())
            addSessionAttribute(EmbSessionAttributes.EMB_STATE, session.appState.name.lowercase(Locale.US))
            addSessionAttribute(EmbSessionAttributes.EMB_CLEAN_EXIT, false.toString())
            addSessionAttribute(EmbSessionAttributes.EMB_TERMINATED, true.toString())

            session.startType.toString().lowercase(Locale.US).let {
                addSessionAttribute(EmbSessionAttributes.EMB_SESSION_START_TYPE, it)
            }
        }
    }

    override fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean) {
        with(destination) {
            addSessionAttribute(EmbSessionAttributes.EMB_CLEAN_EXIT, true.toString())
            addSessionAttribute(EmbSessionAttributes.EMB_TERMINATED, false.toString())
            crashId?.let {
                addSessionAttribute(EmbSessionAttributes.EMB_CRASH_ID, crashId)
            }
            endType?.toString()?.lowercase(Locale.US)?.let {
                addSessionAttribute(EmbSessionAttributes.EMB_SESSION_END_TYPE, it)
            }
            if (coldStart) {
                startupDurationProvider()?.let { duration ->
                    addSessionAttribute(EmbSessionAttributes.EMB_STARTUP_DURATION, duration.toString())
                }
            }

            val logCount = logLimitingService.getCount(LogSeverity.ERROR)
            addSessionAttribute(EmbSessionAttributes.EMB_ERROR_LOG_COUNT, logCount.toString())

            metadataService.getDiskUsage()?.deviceDiskFree?.let { free ->
                addSessionAttribute(EmbSessionAttributes.EMB_DISK_FREE_BYTES, free.toString())
            }
        }
    }
}
