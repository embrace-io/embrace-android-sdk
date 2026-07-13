package io.embrace.android.embracesdk.internal.config.behavior

interface UrlRedactionBehavior {
    fun redactUrl(url: String): String
}
