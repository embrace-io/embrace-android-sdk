package io.embrace.android.embracesdk.internal.telemetry

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.isEmulator
import io.embrace.android.embracesdk.internal.otel.sdk.toEmbraceUsageAttributeName
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for tracking usage of public APIs, and different internal metrics about the app.
 */
internal class EmbraceTelemetryService(
    private val systemInfo: SystemInfo,
) : TelemetryService {

    private val okHttpReflectionFacade: OkHttpReflectionFacade = OkHttpReflectionFacade()
    private val usageCountMap = ConcurrentHashMap<String, Int>()
    private val storageTelemetryMap = ConcurrentHashMap<String, String>()
    private val appliedLimitCountMap = ConcurrentHashMap<String, AtomicInteger>()
    private val appAttributes: Map<String, String> by lazy { computeAppAttributes() }

    override fun onPublicApiCalled(name: String) {
        synchronized(usageCountMap) {
            usageCountMap[name] = (usageCountMap[name] ?: 0) + 1
        }
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        this.storageTelemetryMap.putAll(storageTelemetry)
    }

    override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {
        val id = "applied_limit.$telemetryType.${limitType.attributeName}"
        val key = EmbraceAttributeKey.create(id, isPrivate = true).name
        appliedLimitCountMap.getOrPut(key) { AtomicInteger(0) }.incrementAndGet()
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return getAndClearUsageCountTelemetry()
            .plus(getAndClearStorageTelemetry())
            .plus(getAndClearAppliedLimitTelemetry())
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

    private fun getAndClearAppliedLimitTelemetry(): Map<String, String> {
        val result = appliedLimitCountMap.mapValues { it.value.get().toString() }
        appliedLimitCountMap.clear()
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
