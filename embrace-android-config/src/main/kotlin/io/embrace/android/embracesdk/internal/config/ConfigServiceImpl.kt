package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.ThreadBlockageBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.ConfigEndpoint
import io.embrace.android.embracesdk.internal.config.source.OkHttpRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.NativeSymbols
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Uuid.getEmbUuid
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import okhttp3.OkHttpClient
import okio.ByteString.Companion.decodeBase64
import java.io.File

/**
 * Loads configuration for the app from the Embrace API.
 */
class ConfigServiceImpl(
    private val instrumentedConfig: InstrumentedConfig,
    worker: BackgroundWorker,
    private val serializer: PlatformSerializer,
    store: KeyValueStore,
    okHttpClient: OkHttpClient,
    abis: Array<String>,
    private val sdkVersion: String,
    private val apiLevel: Int,
    private val filesDir: File,
    private val logger: EmbLogger,
    private val hasConfiguredOtelExporters: () -> Boolean,
) : ConfigService {

    private val onlyOtelExportEnabled: Boolean = instrumentedConfig.project.getAppId() == null

    private val remoteConfigStore: RemoteConfigStore = run {
        RemoteConfigStoreImpl(
            serializer = serializer,
            storageDir = File(filesDir, "embrace_remote_config"),
        )
    }

    override val buildInfo: BuildInfo by lazy {
        val cfg = instrumentedConfig.project
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

    override val deviceId: String = run {
        val deviceId = store.getString(DEVICE_IDENTIFIER_KEY)
        if (deviceId != null) {
            return@run deviceId
        }
        val newId = getEmbUuid()
        store.edit {
            putString(DEVICE_IDENTIFIER_KEY, newId)
        }
        newId
    }

    private val remoteConfigSource: RemoteConfigSource? = run {
        if (onlyOtelExportEnabled) return@run null

        OkHttpRemoteConfigSource(
            okhttpClient = okHttpClient,
            serializer = serializer,
            configEndpoint = ConfigEndpoint(
                deviceId,
                buildInfo.versionName,
                instrumentedConfig,
                sdkVersion,
                apiLevel,
            )
        )
    }

    // kick off config HTTP request early so the SDK can't get in a permanently disabled state
    private val combinedRemoteConfigSource: CombinedRemoteConfigSource? = run {
        if (onlyOtelExportEnabled) return@run null
        CombinedRemoteConfigSource(
            store = remoteConfigStore,
            httpSource = lazy { checkNotNull(remoteConfigSource) },
            worker = worker
        ).apply {
            scheduleConfigRequests()
        }
    }

    private val remoteConfig: RemoteConfig? = combinedRemoteConfigSource?.getConfig()

    private val thresholdCheck: BehaviorThresholdCheck = BehaviorThresholdCheck(::deviceId)
    override val backgroundActivityBehavior =
        BackgroundActivityBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val autoDataCaptureBehavior =
        AutoDataCaptureBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val breadcrumbBehavior = BreadcrumbBehaviorImpl(instrumentedConfig, remoteConfig)
    override val sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(instrumentedConfig)
    override val logMessageBehavior = LogMessageBehaviorImpl(remoteConfig)
    override val threadBlockageBehavior = ThreadBlockageBehaviorImpl(thresholdCheck, remoteConfig)
    override val sessionBehavior = SessionBehaviorImpl(remoteConfig)
    override val networkBehavior = NetworkBehaviorImpl(instrumentedConfig, remoteConfig)
    override val dataCaptureEventBehavior = DataCaptureEventBehaviorImpl(remoteConfig)
    override val sdkModeBehavior = SdkModeBehaviorImpl(thresholdCheck, remoteConfig)
    override val appExitInfoBehavior =
        AppExitInfoBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val networkSpanForwardingBehavior =
        NetworkSpanForwardingBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)
    override val otelBehavior = OtelBehaviorImpl(thresholdCheck, instrumentedConfig, remoteConfig)

    override val appId: String? = run {
        val id = instrumentedConfig.project.getAppId()
        require(!id.isNullOrEmpty() || hasConfiguredOtelExporters()) {
            "No appId supplied in embrace-config.json. This is required if you want to " +
                "send data to Embrace, unless you configure an OTel exporter and add" +
                " embrace.disableMappingFileUpload=true to gradle.properties."
        }
        id
    }

    override fun isOnlyUsingOtelExporters(): Boolean = appId.isNullOrEmpty()

    override val appFramework: AppFramework = instrumentedConfig.project.getAppFramework()?.let {
        AppFramework.fromString(it)
    } ?: AppFramework.NATIVE

    override val cpuAbi: CpuAbi = CpuAbi.current(abis)

    override val nativeSymbolMap: Map<String, String>? by lazy {
        getNativeSymbols()?.let {
            val arch = cpuAbi.archName

            when {
                it.symbols.containsKey(arch) -> it.symbols[arch]

                // Uses arm-v7 symbols for arm64 if no symbols for arm64 found.
                arch == ARM_64_NAME -> it.symbols[ARM_ABI_V7_NAME]

                // Uncommon 64 bits arch, uses x86 symbols for x86-64 if no symbols for x86-64 found.
                arch == ARCH_X86_64_NAME -> it.symbols[ARCH_X86_NAME]

                else -> null
            } ?: emptyMap()
        }
    }

    private fun getNativeSymbols(): NativeSymbols? {
        try {
            val encodedSymbols = instrumentedConfig.symbols.getBase64SharedObjectFilesMap() ?: return null
            val decodedSymbols: String = encodedSymbols.decodeBase64()?.utf8() ?: return null
            return serializer.fromJson(decodedSymbols, NativeSymbols::class.java)
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.INVALID_NATIVE_SYMBOLS, ex)
        }

        return null
    }

    private companion object {
        const val ARM_ABI_V7_NAME = "armeabi-v7a"
        const val ARM_64_NAME = "arm64-v8a"
        const val ARCH_X86_NAME = "x86"
        const val ARCH_X86_64_NAME = "x86_64"
        const val DEVICE_IDENTIFIER_KEY = "io.embrace.deviceid"
    }
}
