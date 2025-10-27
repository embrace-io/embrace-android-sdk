package io.embrace.android.embracesdk.internal.arch

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.TraceWriter
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker

/**
 * Provides references to essential functionality that can be used when registering instrumentation via SPI.
 */
interface InstrumentationInstallArgs {

    /**
     * Declares how instrumentation should behave.
     */
    val configService: ConfigService

    /**
     * An interface where events can be written to the session span.
     */
    val sessionSpanWriter: SessionSpanWriter

    /**
     * An interface where tracing events can be written.
     */
    val traceWriter: TraceWriter

    /**
     * Embrace SDK's internal logger.
     */
    val logger: EmbLogger

    /**
     * A clock that can be used for time measurements in telemetry.
     */
    val clock: Clock

    /**
     * The application context
     */
    val context: Context

    /**
     * Retrieves a background worker matching the given name.
     */
    fun backgroundWorker(worker: Worker.Background): BackgroundWorker

    /**
     * Retrieves a system service matching the given name. This may be null if the service
     * is unavailable.
     */
    fun <T> systemService(name: String): T?
}
