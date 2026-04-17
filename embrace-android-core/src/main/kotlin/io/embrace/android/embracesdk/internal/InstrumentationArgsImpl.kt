package io.embrace.android.embracesdk.internal

import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
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
    override val logger: InternalLogger,
    override val clock: Clock,
    override val context: Context,
    override val application: Application,
    override val store: KeyValueStore,
    override val serializer: PlatformSerializer,
    override val ordinalStore: OrdinalStore,
    override val processIdentifier: String,
    override val appStateTracker: AppStateTracker,
    override val navigationTrackingService: NavigationTrackingService,
    override val telemetryService: TelemetryService,
    private val workerThreadModule: WorkerThreadModule,
    private val sessionPartTracker: SessionPartTracker,
    private val userSessionPropertiesService: UserSessionPropertiesService,
    private val userSessionIdProvider: () -> String?,
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

    override fun sessionPartId(): String? = sessionPartTracker.getActiveSessionPartId()

    override fun userSessionId(): String? = userSessionIdProvider()

    override fun userSessionProperties(): Map<String, String> = userSessionPropertiesService.getProperties()

    override fun registerSessionPartChangeListener(listener: SessionPartChangeListener) {
        sessionPartTracker.addSessionPartChangeListener(listener)
    }

    override fun registerSessionPartEndListener(listener: SessionPartEndListener) {
        sessionPartTracker.addSessionPartEndListener(listener)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Context.getSystemServiceSafe(name: String): T? = runCatching {
        getSystemService(name)
    }.getOrNull() as T?
}
