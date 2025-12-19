package io.embrace.android.embracesdk.internal

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class InstrumentationArgsImpl(
    override val configService: ConfigService,
    override val destination: TelemetryDestination,
    override val logger: EmbLogger,
    override val clock: Clock,
    override val context: Context,
    override val application: Application,
    override val store: KeyValueStore,
    override val serializer: PlatformSerializer,
    override val ordinalStore: OrdinalStore,
    override val processIdentifier: String,
    override val appStateTracker: AppStateTracker,
    override val telemetryService: TelemetryService,
    private val workerThreadModule: WorkerThreadModule,
    private val sessionTracker: SessionTracker,
    private val sessionPropertiesService: SessionPropertiesService,
    crashMarkerFileProvider: () -> File,
) : InstrumentationArgs {

    override val crashMarkerFile: File by lazy { crashMarkerFileProvider() }

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker = workerThreadModule.backgroundWorker(worker)
    override fun <T> priorityWorker(
        worker: Worker.Priority,
    ): PriorityWorker<T> = workerThreadModule.priorityWorker(worker)

    private val memo = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> systemService(name: String): T? {
        return memo.getOrPut(name) {
            context.getSystemServiceSafe<T>(name)
        } as? T
    }

    override fun sessionId(): String? = sessionTracker.getActiveSessionId()

    override fun sessionProperties(): Map<String, String> = sessionPropertiesService.getProperties()

    override fun registerSessionChangeListener(listener: SessionChangeListener) {
        sessionTracker.addSessionChangeListener(listener)
    }

    override fun registerSessionEndListener(listener: SessionEndListener) {
        sessionTracker.addSessionEndListener(listener)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Context.getSystemServiceSafe(name: String): T? = runCatching {
        getSystemService(name)
    }.getOrNull() as T?
}
