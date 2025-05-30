package io.embrace.android.embracesdk.internal.telemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.isEmulator
import io.embrace.android.embracesdk.internal.otel.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceUsageAttributeName
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for tracking usage of public APIs, and different internal metrics about the app.
 */
internal class EmbraceTelemetryService(
    private val systemInfo: SystemInfo,
) : TelemetryService {

    private val okHttpReflectionFacade: OkHttpReflectionFacade = OkHttpReflectionFacade()
    private val usageCountMap = ConcurrentHashMap<String, Int>()
    private val storageTelemetryMap = ConcurrentHashMap<String, String>()
    private val appAttributes: Map<String, String> by lazy { computeAppAttributes() }

    override fun onPublicApiCalled(name: String) {
        synchronized(usageCountMap) {
            usageCountMap[name] = (usageCountMap[name] ?: 0) + 1
        }
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        this.storageTelemetryMap.putAll(storageTelemetry)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return getAndClearUsageCountTelemetry()
            .plus(getAndClearStorageTelemetry())
            .plus(appAttributes)
    }

    private fun getAndClearUsageCountTelemetry(): Map<String, String> {
        synchronized(usageCountMap) {
            val usageCountTelemetryMap = usageCountMap.entries.associate {
                it.key.toEmbraceUsageAttributeName() to it.value.toString()
            }
            usageCountMap.clear()
            return usageCountTelemetryMap
        }
    }

    private fun getAndClearStorageTelemetry(): Map<String, String> {
        val result = storageTelemetryMap.toMap()
        storageTelemetryMap.clear()
        return result
    }

    /**
     * Interesting attributes about the running app environment. These should be the same for every session, so we only compute them once.
     */
    private fun computeAppAttributes(): Map<String, String> {
        val appAttributesMap = mutableMapOf<String, String>()

        appAttributesMap[EmbraceAttributeKey.create("okhttp3").name] = okHttpReflectionFacade.hasOkHttp3().toString()

        val okhttp3Version = okHttpReflectionFacade.getOkHttp3Version()
        if (okhttp3Version.isNotEmpty()) {
            appAttributesMap[EmbraceAttributeKey.create("okhttp3_on_classpath").name] = okhttp3Version
        }

        appAttributesMap[EmbraceAttributeKey.create("kotlin_on_classpath").name] =
            runCatching { KotlinVersion.CURRENT.toString() }.getOrDefault("unknown")

        appAttributesMap[EmbraceAttributeKey.create("is_emulator").name] =
            runCatching { systemInfo.isEmulator().toString() }.getOrDefault("unknown")

        return appAttributesMap
    }
}
