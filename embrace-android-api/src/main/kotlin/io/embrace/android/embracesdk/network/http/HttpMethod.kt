package io.embrace.android.embracesdk.network.http

/**
 * Enumeration of supported HTTP request methods.
 *
 *
 * This class is part of the Embrace Public API.
 */
public enum class HttpMethod {
    Get,
    Head,
    Post,
    Put,
    Delete,
    Connect,
    Options,
    Trace,
    Patch,
    ;

    public companion object {
        @Deprecated("Use Get", ReplaceWith("HttpMethod.Get"))
        @JvmField
        public val GET: HttpMethod = Get

        @Deprecated("Use Head", ReplaceWith("HttpMethod.Head"))
        @JvmField
        public val HEAD: HttpMethod = Head

        @Deprecated("Use Post", ReplaceWith("HttpMethod.Post"))
        @JvmField
        public val POST: HttpMethod = Post

        @Deprecated("Use Put", ReplaceWith("HttpMethod.Put"))
        @JvmField
        public val PUT: HttpMethod = Put

        @Deprecated("Use Delete", ReplaceWith("HttpMethod.Delete"))
        @JvmField
        public val DELETE: HttpMethod = Delete

        @Deprecated("Use Connect", ReplaceWith("HttpMethod.Connect"))
        @JvmField
        public val CONNECT: HttpMethod = Connect

        @Deprecated("Use Options", ReplaceWith("HttpMethod.Options"))
        @JvmField
        public val OPTIONS: HttpMethod = Options

        @Deprecated("Use Trace", ReplaceWith("HttpMethod.Trace"))
        @JvmField
        public val TRACE: HttpMethod = Trace

        @Deprecated("Use Patch", ReplaceWith("HttpMethod.Patch"))
        @JvmField
        public val PATCH: HttpMethod = Patch

        /**
         * Given the string representation of the HTTP request method, returns the corresponding HttpMethod enum, or GET if unrecognised.
         */
        @JvmStatic
        public fun fromString(method: String): HttpMethod {
            // We expect that the HTTP method will be specified in English so we forcibly use the US locale.
            return when (method.uppercase()) {
                "GET" -> Get
                "HEAD" -> Head
                "POST" -> Post
                "PUT" -> Put
                "DELETE" -> Delete
                "CONNECT" -> Connect
                "OPTIONS" -> Options
                "TRACE" -> Trace
                "PATCH" -> Patch
                else -> Get
            }
        }
    }
}
