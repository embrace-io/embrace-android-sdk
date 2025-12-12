package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.attrs.embCleanExit
import io.embrace.android.embracesdk.internal.arch.attrs.embColdStart
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashId
import io.embrace.android.embracesdk.internal.arch.attrs.embErrorLogCount
import io.embrace.android.embracesdk.internal.arch.attrs.embFreeDiskBytes
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionEndType
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionStartType
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionStartupDuration
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.embTerminated
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionToken
import java.util.Locale

internal class SessionSpanAttrPopulatorImpl(
    private val destination: TelemetryDestination,
    private val startupDurationProvider: () -> Long?,
    private val logService: LogService,
    private val metadataService: MetadataService,
) : SessionSpanAttrPopulator {

    override fun populateSessionSpanStartAttrs(session: SessionToken) {
        with(destination) {
            addSessionAttribute(embColdStart.name, session.isColdStart.toString())
            addSessionAttribute(embSessionNumber.name, session.number.toString())
            addSessionAttribute(embState.name, session.appState.name.lowercase(Locale.US))
            addSessionAttribute(embCleanExit.name, false.toString())
            addSessionAttribute(embTerminated.name, true.toString())

            session.startType.toString().lowercase(Locale.US).let {
                addSessionAttribute(embSessionStartType.name, it)
            }
        }
    }

    override fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean) {
        with(destination) {
            addSessionAttribute(embCleanExit.name, true.toString())
            addSessionAttribute(embTerminated.name, false.toString())
            crashId?.let {
                addSessionAttribute(embCrashId.name, crashId)
            }
            endType?.toString()?.lowercase(Locale.US)?.let {
                addSessionAttribute(embSessionEndType.name, it)
            }
            if (coldStart) {
                startupDurationProvider()?.let { duration ->
                    addSessionAttribute(embSessionStartupDuration.name, duration.toString())
                }
            }

            val logCount = logService.getErrorLogsCount()
            addSessionAttribute(embErrorLogCount.name, logCount.toString())

            metadataService.getDiskUsage()?.deviceDiskFree?.let { free ->
                addSessionAttribute(embFreeDiskBytes.name, free.toString())
            }
        }
    }
}
