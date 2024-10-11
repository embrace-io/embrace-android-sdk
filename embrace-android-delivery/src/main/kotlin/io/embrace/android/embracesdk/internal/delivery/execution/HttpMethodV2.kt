package io.embrace.android.embracesdk.internal.delivery.execution

import java.util.Locale

enum class HttpMethodV2 {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH;

    companion object {
        private val methodMap = values().associateBy { it.name }

        fun fromString(method: String?): HttpMethodV2? = method?.uppercase(Locale.US)?.let { methodMap[it] }
    }
}
