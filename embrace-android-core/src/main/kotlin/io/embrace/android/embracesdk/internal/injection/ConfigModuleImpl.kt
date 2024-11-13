package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

internal class ConfigModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    remoteConfigStoreProvider: Provider<RemoteConfigStore?> = { null },
) : ConfigModule {

    companion object {
        private const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
    }

    override val okHttpClient by singleton {
        OkHttpClient()
            .newBuilder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(DEFAULT_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override val combinedRemoteConfigSource: CombinedRemoteConfigSource? by singleton {
        configService.appId ?: return@singleton null
        CombinedRemoteConfigSource(
            store = remoteConfigStore,
            httpSource = remoteConfigSource ?: return@singleton null,
            worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
        )
    }

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            ConfigServiceImpl(
                openTelemetryCfg = openTelemetryModule.openTelemetryConfiguration,
                preferencesService = androidServicesModule.preferencesService,
                suppliedFramework = framework,
                instrumentedConfig = initModule.instrumentedConfig,
                configProvider = { combinedRemoteConfigSource?.getConfig() },
            )
        }
    }

    override val remoteConfigSource by singleton {
        val builder = urlBuilder ?: return@singleton null
        OkHttpRemoteConfigSource(
            okhttpClient = okHttpClient,
            apiUrlBuilder = builder,
            serializer = initModule.jsonSerializer,
        )
    }

    override val remoteConfigStore: RemoteConfigStore by singleton {
        remoteConfigStoreProvider() ?: RemoteConfigStoreImpl(
            serializer = initModule.jsonSerializer,
            storageDir = File(coreModule.context.filesDir, "embrace_remote_config"),
        )
    }

    override val urlBuilder: ApiUrlBuilder? by singleton {
        configService.appId ?: return@singleton null
        Systrace.traceSynchronous("url-builder-init") {
            EmbraceApiUrlBuilder(
                deviceId = androidServicesModule.preferencesService.deviceIdentifier,
                appVersionName = coreModule.packageVersionInfo.versionName,
                instrumentedConfig = initModule.instrumentedConfig,
            )
        }
    }
}
