package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.EmbraceSerializer
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.spans.toEmbraceSpanName
import io.embrace.android.embracesdk.payload.AppInfo
import io.embrace.android.embracesdk.payload.Breadcrumbs
import io.embrace.android.embracesdk.payload.DeviceInfo
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo

/**
 * Serializes the session message to JSON in multiple parts. This allows nodes on the JSON tree
 * to be cached as a string if they have not altered since the previous serialization attempt.
 */
internal class SessionMessageSerializer(
    private val serializer: EmbraceSerializer
) : MemoryCleanerListener {

    private val jsonCache = mutableMapOf<String, String>()
    private var prevSession: SessionMessage? = null

    fun serialize(msg: SessionMessage): String {
        synchronized(this) {
            Systrace.trace("serialize-session".toEmbraceSpanName()) {
                val trace = serializer.spansService.createSpan(name = "serialize-session")
                trace?.start()
                try {
                    val json = StringBuilder()
                    json.append("{")

                    serializer.spansService.recordSpan("session", parent = trace, recordSystrace = true) {
                        val session = calculateJsonValue(msg, "s", Session::class.java) { it.session }
                        addJsonProperty("\"s\":", session, json)
                        trace?.addAttribute("session-size", session.length.toString())
                    }

                    serializer.spansService.recordSpan("user-info", parent = trace, recordSystrace = true) {
                        val userInfo = calculateJsonValue(msg, "u", UserInfo::class.java) { it.userInfo }
                        addJsonProperty("\"u\":", userInfo, json)
                        trace?.addAttribute("user-info-size", userInfo.length.toString())
                    }

                    serializer.spansService.recordSpan("app-info", parent = trace, recordSystrace = true) {
                        val appInfo = calculateJsonValue(msg, "a", AppInfo::class.java) { it.appInfo }
                        addJsonProperty("\"a\":", appInfo, json)
                        trace?.addAttribute("app-info-size", appInfo.length.toString())
                    }

                    serializer.spansService.recordSpan("device-info", parent = trace, recordSystrace = true) {
                        val deviceInfo = calculateJsonValue(msg, "d", DeviceInfo::class.java) { it.deviceInfo }
                        addJsonProperty("\"d\":", deviceInfo, json)
                        trace?.addAttribute("device-info-size", deviceInfo.length.toString())
                    }

                    serializer.spansService.recordSpan("performance-info", parent = trace, recordSystrace = true) {
                        val performanceInfo =
                            calculateJsonValue(msg, "p", PerformanceInfo::class.java) { it.performanceInfo }
                        addJsonProperty("\"p\":", performanceInfo, json)
                        trace?.addAttribute("performance-info-size", performanceInfo.length.toString())
                    }

                    serializer.spansService.recordSpan("breadcrumbs", parent = trace, recordSystrace = true) {
                        val breadcrumbs =
                            calculateJsonValue(msg, "br", Breadcrumbs::class.java) { it.breadcrumbs }
                        addJsonProperty("\"br\":", breadcrumbs, json)
                        trace?.addAttribute("breadcrumbs-size", breadcrumbs.length.toString())
                    }

                    serializer.spansService.recordSpan("spans", parent = trace, recordSystrace = true) {
                        val spans = calculateJsonValue(msg, "spans", List::class.java) { it.spans }
                        addJsonProperty("\"spans\":", spans, json)
                        trace?.addAttribute("spans-size", spans.length.toString())
                    }

                    json.append("\"v\":")
                    json.append(ApiClient.MESSAGE_VERSION)
                    json.append("}")
                    prevSession = msg
                    trace?.addAttribute("total-size", json.length.toString())
                    return json.toString()
                } finally {
                    trace?.stop()
                }
            }
        }
    }

    private fun addJsonProperty(key: String, value: String, json: StringBuilder) {
        if (value != "null") {
            json.append(key)
            json.append(value)
            json.append(",")
        }
    }

    private fun <T> calculateJsonValue(
        msg: SessionMessage,
        key: String,
        clz: Class<T>,
        fieldProvider: (sessionMessage: SessionMessage) -> T?
    ): String {
        return runCatching {
            val newValue = fieldProvider(msg) ?: return "null"
            val oldValue: T? = prevSession?.run { fieldProvider(this) }
            val cache = jsonCache[key]
            val isCacheValid = newValue == oldValue
            return when {
                cache != null && isCacheValid -> cache
                else -> return serializer.toJson(newValue, clz).apply {
                    jsonCache[key] = this
                }
            }
        }.getOrElse {
            "null"
        }
    }

    override fun cleanCollections() {
        synchronized(this) {
            jsonCache.clear()
        }
    }
}
