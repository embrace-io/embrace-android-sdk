package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import android.os.Build
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkCallbackConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesServiceImpl
import io.embrace.android.embracesdk.internal.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.id.SessionPartTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateTrackerImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

class EssentialServiceModuleImpl(
    initModule: InitModule,
    configService: ConfigService,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    lifecycleOwnerProvider: Provider<LifecycleOwner?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
) : EssentialServiceModule {

    override val appStateTracker: AppStateTracker by singleton {
        EmbTrace.trace("process-state-service-init") {
            val lifecycleOwner = lifecycleOwnerProvider() ?: ProcessLifecycleOwner.get()
            AppStateTrackerImpl(initModule.logger, lifecycleOwner)
        }
    }

    override val userService: UserService by singleton {
        EmbTrace.trace("user-service-init") {
            EmbraceUserService(
                coreModule.store,
                initModule.clock,
                initModule.logger
            )
        }
    }

    override val networkConnectivityService: NetworkConnectivityService by singleton {
        networkConnectivityServiceProvider() ?: EmbTrace.trace("network-connectivity-service-init") {
            val worker = workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                configService.autoDataCaptureBehavior.isNetworkCallbackConnectivityServiceEnabled()
            ) {
                NetworkCallbackConnectivityService(
                    worker,
                    initModule.logger,
                    coreModule.context.getSystemServiceSafe(Context.CONNECTIVITY_SERVICE),
                )
            } else {
                EmbraceNetworkConnectivityService(
                    coreModule.context,
                    worker,
                    initModule.logger,
                    coreModule.context.getSystemServiceSafe(Context.CONNECTIVITY_SERVICE),
                )
            }
        }
    }

    override val sessionPartTracker: SessionPartTracker by singleton {
        SessionPartTrackerImpl(
            coreModule.context.getSystemServiceSafe(Context.ACTIVITY_SERVICE),
            initModule.logger
        )
    }

    override val userSessionPropertiesService: UserSessionPropertiesService by singleton {
        EmbTrace.trace("session-properties-init") {
            UserSessionPropertiesServiceImpl(
                store = coreModule.store,
                configService = configService,
                destination = telemetryDestination,
                telemetryService = initModule.telemetryService
            )
        }
    }

    override val telemetryDestination: TelemetryDestination by singleton {
        TelemetryDestinationImpl(
            clock = initModule.clock,
            spanService = openTelemetryModule.spanService,
            eventService = openTelemetryModule.eventService,
            currentSessionSpan = openTelemetryModule.currentSessionSpan,
        )
    }
}
