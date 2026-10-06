package io.embrace.android.embracesdk.internal.envelope.resource

import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.payload.EnvelopeResource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class EnvelopeResourceSourceImpl(
    private val configService: ConfigService,
    private val hosted: HostedSdkVersionInfo,
    private val environment: AppEnvironment.Environment,
    private val device: Device,
    private val versionName: String,
    private val versionCode: Int?,
    private val rnBundleIdProvider: () -> String?,
    private val useKotlinSdkProvider: () -> Boolean,
) : EnvelopeResourceSource {

    private val extras = ConcurrentHashMap<String, String>()
    private val listeners = CopyOnWriteArrayList<(EnvelopeResource) -> Unit>()
    private var lastEmitted: EnvelopeResource? = null
    private val lock = Any()

    private val pctRollouts: String? by lazy {
        configService.enabledPctRollouts.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(";")
    }

    private val otelSdkMode: String by lazy {
        if (useKotlinSdkProvider()) OTEL_SDK_MODE_REGULAR else OTEL_SDK_MODE_COMPAT
    }

    override fun getEnvelopeResource(): EnvelopeResource {
        val buildInfo = configService.buildInfo

        return EnvelopeResource(
            appVersion = buildInfo.versionName,
            bundleVersion = buildInfo.versionCode,
            appEcosystemId = buildInfo.packageName,
            appFramework = configService.appFramework,
            buildId = buildInfo.buildId,
            buildType = buildInfo.buildType,
            buildFlavor = buildInfo.buildFlavor,
            environment = environment.value,
            sdkVersion = versionName,
            sdkSimpleVersion = versionCode,
            hostedPlatformVersion = hosted.hostedPlatformVersion,
            hostedSdkVersion = hosted.hostedSdkVersion,
            reactNativeBundleId = rnBundleIdProvider(),
            javascriptPatchNumber = hosted.javaScriptPatchNumber,
            unityBuildId = hosted.unityBuildIdNumber,
            deviceManufacturer = device.systemInfo.deviceManufacturer,
            deviceModel = device.systemInfo.deviceModel,
            deviceArchitecture = device.systemInfo.primaryAbi,
            jailbroken = device.isJailbroken,
            diskTotalCapacity = device.internalStorageTotalCapacity.value,
            osType = device.systemInfo.osType,
            osName = device.systemInfo.osName,
            osVersion = device.systemInfo.osVersion,
            osCode = device.systemInfo.androidOsApiLevel,
            screenResolution = device.screenResolution,
            numCores = device.numberOfCores,
            usesEmmcStorage = device.usesEmmcStorage,
            deviceSocModel = device.socModel,
            extras = buildMap {
                putAll(extras)
                pctRollouts?.let { put(KEY_PCT_ROLLOUTS, it) }
                put(KEY_OTEL_SDK_MODE, otelSdkMode)
            },
        )
    }

    override fun add(key: String, value: String) {
        extras[key] = value
        notifyIfChanged()
    }

    override fun addChangeListener(listener: (EnvelopeResource) -> Unit) {
        listeners.add(listener)
        val current = synchronized(lock) {
            getEnvelopeResource().also { lastEmitted = it }
        }
        notifyListener(listener, current)
    }

    /**
     * Rebuilds the resource and hands it to the listeners if it differs from the one they last saw.
     * Called by whatever owns a value the resource is built from, once that value has changed.
     */
    fun notifyIfChanged() {
        if (listeners.isEmpty()) {
            return
        }
        val changed = synchronized(lock) {
            val current = getEnvelopeResource()
            when (current) {
                lastEmitted -> null
                else -> current.also { lastEmitted = it }
            }
        } ?: return

        listeners.forEach { listener ->
            notifyListener(listener, changed)
        }
    }

    private fun notifyListener(listener: (EnvelopeResource) -> Unit, resource: EnvelopeResource) {
        try {
            listener(resource)
        } catch (ignored: Throwable) {
        }
    }

    companion object {
        const val KEY_PCT_ROLLOUTS: String = "pct_rollouts"
        const val KEY_OTEL_SDK_MODE: String = "otel_sdk_mode"
        const val OTEL_SDK_MODE_REGULAR: String = "regular"
        const val OTEL_SDK_MODE_COMPAT: String = "compat"
    }
}
