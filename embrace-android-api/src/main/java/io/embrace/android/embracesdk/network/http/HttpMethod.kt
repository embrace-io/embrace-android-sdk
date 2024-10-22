package io.embrace.android.embracesdk.network.http

/**
 * Enumeration of supported HTTP request methods.
 *
 *
 * This class is part of the Embrace Public API.
 */
public enum class HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH;

    public companion object {
        /**
         * Given the string representation of the HTTP request method, returns the corresponding HttpMethod enum.
         */
        @JvmStatic
        public fun fromString(method: String): HttpMethod {
            // We expect that the HTTP method will be specified in English so we forcibly use the US locale.
            return when (method.uppercase()) {
                "GET" -> GET
                "HEAD" -> HEAD
                "POST" -> POST
                "PUT" -> PUT
                "DELETE" -> DELETE
                "CONNECT" -> CONNECT
                "OPTIONS" -> OPTIONS
                "TRACE" -> TRACE
                "PATCH" -> PATCH
                else -> error("Invalid HTTP method: $method")
            }
        }
    }
}
