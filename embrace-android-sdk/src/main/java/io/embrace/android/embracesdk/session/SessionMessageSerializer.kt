package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.SessionMessage
import java.io.BufferedWriter
import java.io.StringWriter
import java.io.Writer

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
        val writer = StringWriter()
        serialize(msg, writer)
        return writer.toString()
    }

    fun serialize(msg: SessionMessage, stream: Writer) {
        synchronized(this) {
            stream.buffered().use { writer ->
                writer.serializeImpl(msg)
                prevSession = msg
            }
        }
    }

    private fun BufferedWriter.serializeImpl(msg: SessionMessage) {
        append("{")

        val session = jsonValue(msg, "s", SessionMessage::session)
        addJsonProperty("\"s\":", session)

        val userInfo = jsonValue(msg, "u", SessionMessage::userInfo)
        addJsonProperty("\"u\":", userInfo)

        val appInfo = jsonValue(msg, "a", SessionMessage::appInfo)
        addJsonProperty("\"a\":", appInfo)

        val deviceInfo = jsonValue(msg, "d", SessionMessage::deviceInfo)
        addJsonProperty("\"d\":", deviceInfo)

        val performanceInfo =
            jsonValue(msg, "p", SessionMessage::performanceInfo)
        addJsonProperty("\"p\":", performanceInfo)

        val breadcrumbs = jsonValue(msg, "br", SessionMessage::breadcrumbs)
        addJsonProperty("\"br\":", breadcrumbs)

        val spans = jsonValue(msg, "spans", SessionMessage::spans)
        addJsonProperty("\"spans\":", spans)

        append("\"v\":")
        append("${ApiClient.MESSAGE_VERSION}")
        append("}")
    }

    private fun BufferedWriter.addJsonProperty(key: String, value: String) {
        if (value != "null") {
            append(key)
            append(value)
            append(",")
        }
    }

    private fun <T> jsonValue(
        msg: SessionMessage,
        key: String,
        fieldProvider: (sessionMessage: SessionMessage) -> T?
    ): String {
        return runCatching {
            val newValue = fieldProvider(msg) ?: return "null"
            val oldValue: T? = prevSession?.run { fieldProvider(this) }
            val cache = jsonCache[key]
            val isCacheValid = newValue == oldValue
            return when {
                cache != null && isCacheValid -> cache
                else -> return serializer.toJson(newValue).apply {
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
