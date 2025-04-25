package io.embrace.android.embracesdk.internal.injection

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.embrace.android.embracesdk.internal.EmbTrace
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.destination.LogWriterImpl
import io.embrace.android.embracesdk.internal.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesServiceImpl
import io.embrace.android.embracesdk.internal.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.id.SessionIdTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

class EssentialServiceModuleImpl(
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    lifecycleOwnerProvider: Provider<LifecycleOwner?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
) : EssentialServiceModule {

    private val configService by lazy { configModule.configService }

    override val processStateService: ProcessStateService by singleton {
        EmbTrace.trace("process-state-service-init") {
            val lifecycleOwner = lifecycleOwnerProvider() ?: ProcessLifecycleOwner.get()
            EmbraceProcessStateService(initModule.clock, initModule.logger, lifecycleOwner)
        }
    }

    override val activityLifecycleTracker: ActivityLifecycleTracker by singleton {
        ActivityLifecycleTracker(coreModule.application, initModule.logger)
    }

    override val userService: UserService by singleton {
        EmbTrace.trace("user-service-init") {
            EmbraceUserService(
                androidServicesModule.preferencesService,
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
                systemServiceModule.connectivityManager
            )
        }
    }

    override val sessionIdTracker: SessionIdTracker by singleton {
        SessionIdTrackerImpl(systemServiceModule.activityManager, initModule.logger)
    }

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        EmbTrace.trace("session-properties-init") {
            SessionPropertiesServiceImpl(
                preferencesService = androidServicesModule.preferencesService,
                configService = configService,
                writer = openTelemetryModule.currentSessionSpan
            )
        }
    }

    override val logWriter: LogWriter by singleton {
        LogWriterImpl(
            logger = openTelemetryModule.logger,
            sessionIdTracker = sessionIdTracker,
            processStateService = processStateService,
            clock = initModule.clock,
        )
    }
}
