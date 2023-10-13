package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.EmbraceSerializer
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
            val json = StringBuilder()
            json.append("{")

            val session = calculateJsonValue(msg, "s", Session::class.java) { it.session }
            addJsonProperty("\"s\":", session, json)

            val userInfo = calculateJsonValue(msg, "u", UserInfo::class.java) { it.userInfo }
            addJsonProperty("\"u\":", userInfo, json)

            val appInfo = calculateJsonValue(msg, "a", AppInfo::class.java) { it.appInfo }
            addJsonProperty("\"a\":", appInfo, json)

            val deviceInfo = calculateJsonValue(msg, "d", DeviceInfo::class.java) { it.deviceInfo }
            addJsonProperty("\"d\":", deviceInfo, json)

            val performanceInfo =
                calculateJsonValue(msg, "p", PerformanceInfo::class.java) { it.performanceInfo }
            addJsonProperty("\"p\":", performanceInfo, json)

            val breadcrumbs =
                calculateJsonValue(msg, "br", Breadcrumbs::class.java) { it.breadcrumbs }
            addJsonProperty("\"br\":", breadcrumbs, json)

            val spans = calculateJsonValue(msg, "spans", List::class.java) { it.spans }
            addJsonProperty("\"spans\":", spans, json)

            json.append("\"v\":")
            json.append(ApiClient.MESSAGE_VERSION)
            json.append("}")
            prevSession = msg
            return json.toString()
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
