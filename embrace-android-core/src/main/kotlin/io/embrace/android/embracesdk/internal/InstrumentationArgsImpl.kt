package io.embrace.android.embracesdk.internal

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.WorkerThreadPool
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.network.http.HttpRequestInfoModifierChain
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class InstrumentationArgsImpl(
    override val configService: ConfigService,
    override val destination: TelemetryDestination,
    override val errorHandler: ErrorHandler,
    override val clock: Clock,
    override val context: Context,
    override val application: Application,
    storeProvider: () -> KeyValueStore,
    override val serializer: PlatformSerializer,
    override val uuidSource: UuidSource,
    override val ordinalStore: OrdinalStore,
    override val processIdentifier: String,
    override val processStateTracker: ProcessStateTracker,
    override val navigationTrackingService: NavigationTrackingService,
    override val telemetryService: InternalTelemetryService,
    private val workerThreadPool: WorkerThreadPool,
    private val sessionPartTracker: SessionPartTracker,
    private val userSessionPropertiesService: UserSessionPropertiesService,
    private val userSessionIdsProvider: () -> String?,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
    crashMarkerFileProvider: () -> File,
    override val eventBus: EventBus,
) : InstrumentationArgs {

    override val crashMarkerFile: File by lazy { crashMarkerFileProvider() }

    override val store: KeyValueStore by lazy { storeProvider() }

    override val packageInfo: PackageInfo? by lazy {
        errorHandler.guardSdkCode(SdkErrorType.Ignored, null) { context.packageManager.getPackageInfo(context.packageName, 0) }
    }

    override val httpRequestInfoModifierChain: HttpRequestInfoModifierChain = HttpRequestInfoModifierChain(errorHandler)

    override fun backgroundWorker(worker: Worker.Background): BackgroundWorker = workerThreadPool.backgroundWorker(worker)
    override fun <T> priorityWorker(
        worker: Worker.Priority,
    ): PriorityWorker<T> = workerThreadPool.priorityWorker(worker)

    private val memo = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <T> systemService(name: String): T? {
        return memo.getOrPut(name) {
            context.getSystemServiceSafe<T>(name)
        } as? T
    }

    override fun sessionPartId(): String? = sessionPartTracker.getActiveSessionPartId()

    override fun userSessionId(): String? = userSessionIdsProvider()

    override fun activeSessionIds(): SessionIdsSnapshot = activeSessionIdsProvider()

    override fun userSessionProperties(): Map<String, String> = userSessionPropertiesService.getProperties()

    override fun registerSessionPartChangeListener(listener: SessionPartChangeListener) {
        sessionPartTracker.addSessionPartChangeListener(listener)
    }

    override fun registerSessionPartEndListener(listener: SessionPartEndListener) {
        sessionPartTracker.addSessionPartEndListener(listener)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Context.getSystemServiceSafe(name: String): T? =
        errorHandler.guardSdkCode(SdkErrorType.Ignored, null) { getSystemService(name) } as T?
}
