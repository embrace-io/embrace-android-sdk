package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.internal.EventType

@JsonClass(generateAdapter = true)
internal data class Event constructor(
    @Json(name = "n")
    @JvmField
    val name: String? = null,

    @Json(name = "li")
    @JvmField
    val messageId: String? = null,

    @Json(name = "id")
    @JvmField
    val eventId: String,

    @Json(name = "si")
    @JvmField
    val sessionId: String? = null,

    @Json(name = "t")
    @JvmField
    val type: EventType,

    @Json(name = "ts")
    @JvmField
    val timestamp: Long? = null,

    @Json(name = "th")
    @JvmField
    val lateThreshold: Long? = null,

    @Json(name = "sc")
    @JvmField
    val screenshotTaken: Boolean? = false,

    @Json(name = "du")
    @JvmField
    val duration: Long? = null,

    @Json(name = "st")
    @JvmField
    val appState: String? = null,

    @Json(name = "pr")
    val customProperties: Map<String, Any>? = null,

    @Json(name = "sp")
    val sessionProperties: Map<String, String>? = null,

    @Transient
    val activeEventIds: List<String>? = null,

    @Json(name = "et")
    @JvmField
    val logExceptionType: String? = null,

    @Json(name = "en")
    val exceptionName: String? = null,

    @Json(name = "em")
    val exceptionMessage: String? = null,

    @Json(name = "f")
    @JvmField
    val framework: Int? = null,
)
