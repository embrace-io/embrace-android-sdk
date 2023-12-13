package io.embrace.android.embracesdk.telemetry

import io.embrace.android.embracesdk.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.spans.toEmbraceAttributeName

/*
    Service for tracking usage of public APIs, and different internal metrics about the app.
 */
internal class EmbraceTelemetryService(
    private val okHttpReflectionFacade: OkHttpReflectionFacade = OkHttpReflectionFacade()
) {

    private val usageCountMap = mutableMapOf<String, Int>()
    private val appAttributesMap = mutableMapOf<String, String>()

    /*
        Tracks the usage of a public API by name. Adds a suffix for easier identification.
    */
    fun onPublicApiCalled(name: String) {
        val suffixedName = "usage - $name"
        usageCountMap[suffixedName] = (usageCountMap[suffixedName] ?: 0) + 1
    }

    /*
        Returns a map with every telemetry value. For now, it's just usage counts of public APIs.
     */
    fun getTelemetryAttributes(): Map<String, String> {
        val telemetryMap = mutableMapOf<String, String>()

        telemetryMap.putAll(getUsageCountTelemetry())

        telemetryMap.putAll(getAppAttributes())

        return telemetryMap
    }

    private fun getUsageCountTelemetry() = usageCountMap.mapValues {
        it.value.toString()
    }.also {
        usageCountMap.clear()
    }

    /**
     * Interesting attributes about the running app environment. These should be the same for every session, so we only compute them once.
     */
    private fun getAppAttributes(): Map<String, String> {
        if (appAttributesMap.isEmpty()) {
            appAttributesMap.putAll(getOkHttpAttributes())

            appAttributesMap["kotlin_on_classpath".toEmbraceAttributeName()] =
                runCatching { KotlinVersion.CURRENT.toString() }.getOrDefault("unknown")

            appAttributesMap["is_emulator".toEmbraceAttributeName()] =
                runCatching { EmbraceMetadataService.isEmulator().toString() }.getOrDefault("unknown")
        }

        return appAttributesMap
    }

    private fun getOkHttpAttributes(): Map<String, String> {
        val okHttpAttributes = mutableMapOf<String, String>()

        okHttpAttributes["okhttp3".toEmbraceAttributeName()] = okHttpReflectionFacade.hasOkHttp3().toString()

        val okhttp3Version = okHttpReflectionFacade.getOkHttp3Version()
        if (okhttp3Version.isNotEmpty()) {
            okHttpAttributes["okhttp3_on_classpath".toEmbraceAttributeName()] = okhttp3Version
        }

        return okHttpAttributes
    }
}
