package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.Worker
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

class ConfigModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
) : ConfigModule {

    companion object {
        private const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
    }

    override val okHttpClient by singleton {
        EmbTrace.trace("okhttp-client-init") {
            OkHttpClient()
                .newBuilder()
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .connectTimeout(DEFAULT_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }

    override val combinedRemoteConfigSource: CombinedRemoteConfigSource? by singleton {
        if (initModule.onlyOtelExportEnabled()) return@singleton null
        CombinedRemoteConfigSource(
            store = remoteConfigStore,
            httpSource = lazy { checkNotNull(remoteConfigSource) },
            worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
        )
    }

    override val configService: ConfigService by singleton {
        EmbTrace.trace("config-service-init") {
            ConfigServiceImpl(
                instrumentedConfig = initModule.instrumentedConfig,
                remoteConfig = combinedRemoteConfigSource?.getConfig(),
                deviceIdSupplier = androidServicesModule.preferencesService::deviceIdentifier,
                hasConfiguredOtelExporters = openTelemetryModule.otelSdkConfig::hasConfiguredOtelExporters,
            )
        }
    }

    override val remoteConfigSource: RemoteConfigSource? by singleton {
        if (initModule.onlyOtelExportEnabled()) return@singleton null
        OkHttpRemoteConfigSource(
            okhttpClient = okHttpClient,
            apiUrlBuilder = urlBuilder ?: return@singleton null,
            serializer = initModule.jsonSerializer,
        )
    }

    private val remoteConfigStore: RemoteConfigStore by singleton {
        RemoteConfigStoreImpl(
            serializer = initModule.jsonSerializer,
            storageDir = File(coreModule.context.filesDir, "embrace_remote_config"),
        )
    }

    override val urlBuilder: ApiUrlBuilder? by singleton {
        if (initModule.onlyOtelExportEnabled()) return@singleton null
        EmbTrace.trace("url-builder-init") {
            EmbraceApiUrlBuilder(
                deviceId = androidServicesModule.preferencesService.deviceIdentifier,
                appVersionName = coreModule.packageVersionInfo.versionName,
                instrumentedConfig = initModule.instrumentedConfig,
            )
        }
    }

    private fun InitModule.onlyOtelExportEnabled(): Boolean {
        instrumentedConfig.project.getAppId() ?: return true
        return false
    }
}
