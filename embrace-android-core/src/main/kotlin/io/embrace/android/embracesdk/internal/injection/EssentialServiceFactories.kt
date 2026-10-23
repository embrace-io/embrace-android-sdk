package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkCallbackConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingServiceImpl
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesServiceImpl
import io.embrace.android.embracesdk.internal.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.navigation.NavigationTrackingServiceImpl
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProviderImpl
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.id.SessionPartTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateTrackerImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

fun createProcessStateTracker(
    errorHandler: ErrorHandler,
    lifecycleTracker: LifecycleTracker,
): ProcessStateTracker = ProcessStateTrackerImpl(errorHandler, lifecycleTracker)

fun createNavigationTrackingService(): NavigationTrackingService = NavigationTrackingServiceImpl()

fun createNetworkConnectivityService(
    context: Context,
    configService: ConfigService,
    worker: BackgroundWorker,
    errorHandler: ErrorHandler,
): NetworkConnectivityService {
    val connectivityManager: Lazy<ConnectivityManager?> = lazy {
        EmbTrace.trace("connectivity-manager-init") {
            context.getSystemServiceSafe<ConnectivityManager>(Context.CONNECTIVITY_SERVICE)
        }
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
        configService.config.autoDataCapture.networkCallbackConnectivityServiceEnabled
    ) {
        NetworkCallbackConnectivityService(worker, errorHandler, connectivityManager)
    } else {
        EmbraceNetworkConnectivityService(
            context = context,
            backgroundWorker = worker,
            errorHandler = errorHandler,
            connectivityAvailable = { connectivityManager.value != null },
            activeNetworkInfo = @Suppress("DEPRECATION") { connectivityManager.value?.activeNetworkInfo },
        )
    }
}

fun createSessionPartTracker(
    context: Context,
    errorHandler: ErrorHandler,
): SessionPartTracker {
    val activityManager: Lazy<ActivityManager?> = lazy {
        EmbTrace.trace("activity-manager-init") {
            context.getSystemServiceSafe<ActivityManager>(Context.ACTIVITY_SERVICE)
        }
    }
    return SessionPartTrackerImpl(activityManager, errorHandler)
}

fun createSessionIdsProvider(
    sessionOrchestratorProvider: () -> SessionOrchestrator?,
    sessionPartTracker: SessionPartTracker,
): SessionIdsProvider = SessionIdsProviderImpl(sessionOrchestratorProvider, sessionPartTracker)

fun createUserSessionPropertiesService(
    store: Lazy<KeyValueStore>,
    configService: ConfigService,
    destination: TelemetryDestination,
    telemetryService: InternalTelemetryService,
): UserSessionPropertiesService = UserSessionPropertiesServiceImpl(store, configService, destination, telemetryService)

fun createUserService(
    store: KeyValueStore,
    clock: Clock,
    errorHandler: ErrorHandler,
): UserService = EmbraceUserService(store, clock, errorHandler).apply { recordInstallDateIfAbsent() }

fun createExperimentTrackingService(
    configService: ConfigService,
    telemetryService: InternalTelemetryService,
    telemetryDestination: TelemetryDestination,
): ExperimentTrackingService = ExperimentTrackingServiceImpl(configService, telemetryService, telemetryDestination)
