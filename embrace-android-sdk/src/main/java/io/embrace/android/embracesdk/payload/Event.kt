package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.EmbraceEvent.Type

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
    val type: Type,

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

    @Transient
    private val customProperties: Map<String, Any>? = null,

    @Transient
    private val sessionProperties: Map<String, String>? = null,

    @Transient
    private val activeEventIdsList: List<String>? = null,

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
) {

    @Json(name = "pr")
    val customPropertiesMap: Map<String, Any>? = customProperties?.toMutableMap()

    @Json(name = "sp")
    val sessionPropertiesMap: Map<String, String>? = sessionProperties?.toMutableMap()

    @Transient
    val activeEventIds: List<String>? = activeEventIdsList?.toMutableList()
}
