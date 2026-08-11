package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkCallbackConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesServiceImpl
import io.embrace.android.embracesdk.internal.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.navigation.NavigationTrackingServiceImpl
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProviderImpl
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.id.SessionPartTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.createLifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

class EssentialServiceModuleImpl(
    initModule: InitModule,
    configService: ConfigService,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    startupContext: Context?,
    lifecycleTrackerProvider: Provider<LifecycleTracker?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
    private val sessionOrchestratorProvider: Provider<SessionOrchestrator>,
) : EssentialServiceModule {

    override val processStateTracker: ProcessStateTracker = EmbTrace.trace("process-state-service-init") {
        val lifecycleTracker = lifecycleTrackerProvider()
            ?: createLifecycleTracker(configService, coreModule.application, startupContext)
        ProcessStateTrackerImpl(initModule.logger, lifecycleTracker)
            .apply { register() }
    }

    override val navigationTrackingService: NavigationTrackingService = NavigationTrackingServiceImpl()

    private val connectivityManager: Lazy<ConnectivityManager?> = lazy {
        coreModule.context.getSystemServiceSafe<ConnectivityManager>(Context.CONNECTIVITY_SERVICE)
    }

    override val networkConnectivityService: NetworkConnectivityService =
        networkConnectivityServiceProvider() ?: EmbTrace.trace("network-connectivity-service-init") {
            val worker = workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                configService.autoDataCaptureBehavior.isNetworkCallbackConnectivityServiceEnabled()
            ) {
                NetworkCallbackConnectivityService(
                    worker,
                    initModule.logger,
                    connectivityManager,
                )
            } else {
                EmbraceNetworkConnectivityService(
                    coreModule.context,
                    worker,
                    initModule.logger,
                    connectivityManager,
                )
            }
        }

    private val activityManager: Lazy<ActivityManager?> = lazy {
        coreModule.context.getSystemServiceSafe<ActivityManager>(Context.ACTIVITY_SERVICE)
    }

    override val sessionPartTracker: SessionPartTracker = SessionPartTrackerImpl(
        activityManager,
        initModule.logger,
    )

    override val sessionIdsProvider: SessionIdsProvider =
        SessionIdsProviderImpl(sessionOrchestratorProvider, sessionPartTracker)

    override val telemetryDestination: TelemetryDestination = TelemetryDestinationImpl(
        clock = initModule.clock,
        spanService = openTelemetryModule.spanService,
        loggerSupplier = { openTelemetryModule.otelSdkWrapper.sdkLogger },
        currentSessionPartSpan = openTelemetryModule.currentSessionPartSpan,
    )

    override val userSessionPropertiesService: UserSessionPropertiesService =
        EmbTrace.trace("session-properties-init") {
            UserSessionPropertiesServiceImpl(
                store = lazy { coreModule.store },
                configService = configService,
                destination = telemetryDestination,
                telemetryService = initModule.telemetryService,
            )
        }

    override val userService: UserService by lazy {
        EmbTrace.trace("user-service-init") {
            EmbraceUserService(
                coreModule.store,
                initModule.clock,
                initModule.logger,
            )
        }
    }
}
