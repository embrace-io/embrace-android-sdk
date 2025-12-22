package io.embrace.android.embracesdk.internal.injection

import android.content.pm.ApplicationInfo
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolServiceImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.internal.worker.Worker
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

class ConfigModuleImpl(
    initModule: InitModule,
    private val coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
) : ConfigModule {

    companion object {
        private const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10L
        private const val DEFAULT_READ_TIMEOUT_SECONDS = 60L
        private const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
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
                deviceIdSupplier = ::deviceIdentifier,
                hasConfiguredOtelExporters = openTelemetryModule.otelSdkConfig::hasConfiguredOtelExporters,
            )
        }
    }

    private val remoteConfigSource: RemoteConfigSource? by singleton {
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
                deviceId = deviceIdentifier,
                appVersionName = buildInfo.versionName,
                instrumentedConfig = initModule.instrumentedConfig,
            )
        }
    }

    override val appEnvironment: AppEnvironment by lazy {
        val context = coreModule.context
        val isDebug: Boolean = with(context.applicationInfo) {
            flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        }
        AppEnvironment(isDebug)
    }

    override val buildInfo: BuildInfo by lazy {
        val cfg = initModule.instrumentedConfig.project
        BuildInfo(
            cfg.getBuildId(),
            cfg.getBuildType(),
            cfg.getBuildFlavor(),
            cfg.getReactNativeBundleId(),
            cfg.getVersionName() ?: "UNKNOWN",
            cfg.getVersionCode() ?: "UNKNOWN",
            cfg.getPackageName() ?: "UNKNOWN",
        )
    }

    override val cpuAbi: CpuAbi by singleton {
        CpuAbi.current()
    }

    private fun InitModule.onlyOtelExportEnabled(): Boolean {
        instrumentedConfig.project.getAppId() ?: return true
        return false
    }

    override val nativeSymbolMap: Map<String, String>? by lazy {
        SymbolServiceImpl(
            cpuAbi,
            initModule.jsonSerializer,
            initModule.logger,
            initModule.instrumentedConfig,
        ).symbolsForCurrentArch
    }

    override val deviceIdentifier: String by lazy {
        val deviceId = coreModule.store.getString(DEVICE_IDENTIFIER_KEY)
        if (deviceId != null) {
            return@lazy deviceId
        }
        val newId = getEmbUuid()
        coreModule.store.edit {
            putString(DEVICE_IDENTIFIER_KEY, newId)
        }
        newId
    }
}
