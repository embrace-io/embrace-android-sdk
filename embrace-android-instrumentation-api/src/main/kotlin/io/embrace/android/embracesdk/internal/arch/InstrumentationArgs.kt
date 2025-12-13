package io.embrace.android.embracesdk.internal.arch

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

/**
 * Provides references to essential functionality that can be used when registering instrumentation via SPI.
 */
interface InstrumentationArgs {

    /**
     * Declares how instrumentation should behave.
     */
    val configService: ConfigService

    /**
     * Tracks app state (foreground/background)
     */
    val appStateTracker: AppStateTracker

    /**
     * An interface where telemetry can be written.
     */
    val destination: TelemetryDestination

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
     * The application context
     */
    val application: Application

    /**
     * A key-value store that is shared by all instrumentation used by this SDK. Individual
     * instrumentation should write values to this using their own abstractions, and
     * should make good efforts to use unique keys.
     */
    val store: KeyValueStore

    /**
     * A serializer that can be used to serialize and deserialize data.
     */
    val serializer: PlatformSerializer

    /**
     * Persists ordinals that are cross-cutting concerns across the SDK, such as session number
     * and crash number.
     */
    val ordinalStore: OrdinalStore

    /**
     * The CPU's ABI
     */
    val cpuAbi: CpuAbi

    /**
     * Retrieves a background worker matching the given name.
     */
    fun backgroundWorker(worker: Worker.Background): BackgroundWorker

    /**
     * Retrieves a background worker matching the given name.
     */
    fun <T> priorityWorker(worker: Worker.Priority): PriorityWorker<T>

    /**
     * Retrieves a system service matching the given name. This may be null if the service
     * is unavailable.
     */
    fun <T> systemService(name: String): T?

    /**
     * Retrieves the current session ID, or null if there is no active session.
     */
    fun sessionId(): String?

    /**
     * Identifier that uniquely identifies the current process.
     */
    val processIdentifier: String

    /**
     * Retrieves a snapshot of the current session properties
     */
    fun sessionProperties(): Map<String, String>

    /**
     * The current native symbols.
     */
    val symbols: Map<String, String>?

    /**
     * Retrieves the crash marker file.
     */
    val crashMarkerFile: File

    /**
     * Sets a listener that is invoked after a session changes.
     */
    fun registerSessionChangeListener(listener: SessionChangeListener)

    /**
     * Sets a listener that is invoked before a session ends.
     */
    fun registerSessionEndListener(listener: SessionEndListener)
}
