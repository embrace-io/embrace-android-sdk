package io.embrace.android.embracesdk.internal.vitals

import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.semconv.EmbScreenAttributes

/**
 * Records every [ScreenStart] and [ScreenStop] as an event on one `emb-screen` span per session part.
 */
internal class ScreenSpanRecorder(
    private val destination: TelemetryDestination,
    private val clock: Clock,
    private val errorHandler: ErrorHandler,
    private val sessionPartId: () -> String?,
) : SessionPartEndListener, SessionPartChangeListener {

    private val lock = Any()
    private var span: SpanToken? = null
    private var active = false

    fun onStart(start: ScreenStart) {
        synchronized(lock) {
            span?.addSystemEvent(
                SCREEN_START_EVENT,
                start.timestampMs,
                mapOf(
                    EmbScreenAttributes.EMB_SCREEN_ID to screenId(start.source, start.activityId),
                    EmbScreenAttributes.EMB_SCREEN_NAME to start.name,
                    EmbScreenAttributes.EMB_SCREEN_SOURCE to start.source.value,
                ),
            )
        }
    }

    /**
     * Registered to run after every other handler, so that everything written for the screen is recorded.
     */
    fun onStop(stop: ScreenStop) {
        val written = stop.writtenAttributes()
        synchronized(lock) {
            span?.addSystemEvent(
                SCREEN_STOP_EVENT,
                stop.timestampMs,
                written + mapOf(
                    EmbScreenAttributes.EMB_SCREEN_ID to screenId(stop.source, stop.activityId),
                    EmbScreenAttributes.EMB_SCREEN_STOP_REASON to stop.reason.value,
                ),
            )
        }
    }

    /**
     * Starts recording, beginning with a span for the session part already active, if there is one.
     */
    fun start() {
        synchronized(lock) {
            active = true
            if (span == null) {
                startSpan()
            }
        }
    }

    /**
     * Stops recording, ending the current span. Session part listeners can't be removed, so later parts are ignored.
     */
    fun stop() {
        synchronized(lock) {
            active = false
            span?.stop()
            span = null
        }
    }

    override fun onPreSessionEnd() {
        synchronized(lock) {
            span?.stop()
            span = null
        }
    }

    override fun onPostSessionChange() {
        synchronized(lock) {
            if (active) {
                startSpan()
            }
        }
    }

    private fun startSpan() {
        if (sessionPartId() == null) {
            return
        }
        errorHandler.guardSdkCode(SdkErrorType.DataSourceDataCaptureFail) {
            // stop the previous span, if there is one
            span?.stop()

            span = destination.startSpanCapture(
                name = SPAN_NAME,
                startTimeMs = clock.now(),
                type = EmbType.Ux.Screen,
                private = true,
            )
        }
    }

    private fun screenId(source: ScreenSource, activityId: Int): String = "${source.value}-$activityId"

    private companion object {
        const val SPAN_NAME = "screen"
        const val SCREEN_START_EVENT = "screen_start"
        const val SCREEN_STOP_EVENT = "screen_stop"
    }
}
