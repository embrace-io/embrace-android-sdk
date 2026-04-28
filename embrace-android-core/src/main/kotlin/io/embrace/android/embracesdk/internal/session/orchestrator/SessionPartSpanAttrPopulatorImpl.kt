package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import java.util.Locale

internal class SessionPartSpanAttrPopulatorImpl(
    private val destination: TelemetryDestination,
    private val startupDurationProvider: () -> Long?,
    private val logLimitingService: LogLimitingService,
    private val metadataService: MetadataService,
) : SessionPartSpanAttrPopulator {

    override fun populateSessionSpanStartAttrs(sessionPart: SessionPartToken, userSession: UserSessionMetadata?) {
        with(destination) {
            addSessionPartAttribute(EmbSessionAttributes.EMB_COLD_START, sessionPart.isColdStart.toString())
            addSessionPartAttribute(EmbSessionAttributes.EMB_STATE, sessionPart.appState.name.lowercase(Locale.US))
            addSessionPartAttribute(EmbSessionAttributes.EMB_CLEAN_EXIT, false.toString())
            addSessionPartAttribute(EmbSessionAttributes.EMB_TERMINATED, true.toString())

            sessionPart.startType.toString().lowercase(Locale.US).let {
                addSessionPartAttribute(EmbSessionAttributes.EMB_SESSION_START_TYPE, it)
            }

            if (userSession != null) {
                addSessionPartAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionPart.sessionPartId)
                addSessionPartAttribute(EmbSessionAttributes.EMB_USER_SESSION_PART_NUMBER, sessionPart.sessionPartNumber.toString())
                userSession.attributes.forEach { (key, value) ->
                    addSessionPartAttribute(key, value.toString())
                }
            } else {
                addSessionPartAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "")
                addSessionPartAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, "")
                addSessionPartAttribute(SessionAttributes.SESSION_ID, "")
            }
        }
    }

    override fun populateSessionSpanEndAttrs(
        endType: LifeEventType?,
        crashId: String?,
        coldStart: Boolean,
        endAttributes: Map<String, String>,
    ) {
        with(destination) {
            addSessionPartAttribute(EmbSessionAttributes.EMB_CLEAN_EXIT, true.toString())
            addSessionPartAttribute(EmbSessionAttributes.EMB_TERMINATED, false.toString())
            crashId?.let {
                addSessionPartAttribute(EmbSessionAttributes.EMB_CRASH_ID, crashId)
            }
            endType?.toString()?.lowercase(Locale.US)?.let {
                addSessionPartAttribute(EmbSessionAttributes.EMB_SESSION_END_TYPE, it)
            }
            if (coldStart) {
                startupDurationProvider()?.let { duration ->
                    addSessionPartAttribute(EmbSessionAttributes.EMB_STARTUP_DURATION, duration.toString())
                }
            }

            val logCount = logLimitingService.getCount(LogSeverity.ERROR)
            addSessionPartAttribute(EmbSessionAttributes.EMB_ERROR_LOG_COUNT, logCount.toString())

            metadataService.getDiskUsage()?.deviceDiskFree?.let { free ->
                addSessionPartAttribute(EmbSessionAttributes.EMB_DISK_FREE_BYTES, free.toString())
            }

            endAttributes.forEach { (key, value) ->
                addSessionPartAttribute(key, value)
            }
        }
    }
}
