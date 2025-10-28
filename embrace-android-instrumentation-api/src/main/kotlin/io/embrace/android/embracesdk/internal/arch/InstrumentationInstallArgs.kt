package io.embrace.android.embracesdk.internal.arch

import android.content.Context
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.store.KeyValueStore
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
     * An interface where telemetry can be written.
     */
    val telemetryDestination: TelemetryDestination

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
     * A key-value store that is shared by all instrumentation used by this SDK. Individual
     * instrumentation should write values to this using their own abstractions, and
     * should make good efforts to use unique keys.
     */
    val store: KeyValueStore

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
