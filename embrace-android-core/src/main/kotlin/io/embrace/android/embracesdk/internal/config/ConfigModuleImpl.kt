package io.embrace.android.embracesdk.internal.config

import android.os.Build
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.ConfigEndpoint
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolServiceImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

class ConfigModuleImpl(
    initModule: InitModule,
    private val coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
) : ConfigModule {

    companion object {
        private const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
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
            okhttpClient = initModule.okHttpClient,
            serializer = initModule.jsonSerializer,
            configEndpoint = ConfigEndpoint(
                deviceIdentifier,
                buildInfo.versionName,
                initModule.instrumentedConfig,
                BuildConfig.VERSION_NAME,
                Build.VERSION.SDK_INT,
            )
        )
    }

    private val remoteConfigStore: RemoteConfigStore by singleton {
        RemoteConfigStoreImpl(
            serializer = initModule.jsonSerializer,
            storageDir = File(coreModule.context.filesDir, "embrace_remote_config"),
        )
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
