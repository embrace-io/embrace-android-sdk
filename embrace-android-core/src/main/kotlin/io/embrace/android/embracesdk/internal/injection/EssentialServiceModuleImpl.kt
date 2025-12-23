package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesServiceImpl
import io.embrace.android.embracesdk.internal.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.ConfigModule
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.session.id.SessionTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateTrackerImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
class EssentialServiceModuleImpl(
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    lifecycleOwnerProvider: Provider<LifecycleOwner?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
) : EssentialServiceModule {

    private val configService by lazy { configModule.configService }

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
            EmbraceNetworkConnectivityService(
                coreModule.context,
                workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                initModule.logger,
                coreModule.context.getSystemServiceSafe(Context.CONNECTIVITY_SERVICE),
            )
        }
    }

    override val sessionTracker: SessionTracker by singleton {
        SessionTrackerImpl(
            coreModule.context.getSystemServiceSafe(Context.ACTIVITY_SERVICE),
            initModule.logger
        )
    }

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        EmbTrace.trace("session-properties-init") {
            SessionPropertiesServiceImpl(
                store = coreModule.store,
                configService = configService,
                destination = telemetryDestination
            )
        }
    }

    override val telemetryDestination: TelemetryDestination by singleton {
        TelemetryDestinationImpl(
            logger = openTelemetryModule.otelSdkWrapper.logger,
            sessionTracker = sessionTracker,
            appStateTracker = appStateTracker,
            clock = initModule.clock,
            spanService = openTelemetryModule.spanService,
            currentSessionSpan = openTelemetryModule.currentSessionSpan,
        )
    }
}
