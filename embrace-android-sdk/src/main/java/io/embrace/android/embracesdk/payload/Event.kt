package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.EmbraceEvent.Type

internal data class Event constructor(
    @SerializedName("n")
    @JvmField
    val name: String? = null,

    @SerializedName("li")
    @JvmField
    val messageId: String? = null,

    @SerializedName("id")
    @JvmField
    val eventId: String,

    @SerializedName("si")
    @JvmField
    val sessionId: String? = null,

    @SerializedName("t")
    @JvmField
    val type: Type,

    @SerializedName("ts")
    @JvmField
    val timestamp: Long? = null,

    @SerializedName("th")
    @JvmField
    val lateThreshold: Long? = null,

    @SerializedName("sc")
    @JvmField
    val screenshotTaken: Boolean? = false,

    @SerializedName("du")
    @JvmField
    val duration: Long? = null,

    @SerializedName("st")
    @JvmField
    val appState: String? = null,

    @SerializedName("pr")
    val customProperties: Map<String, Any>? = null,

    @SerializedName("sp")
    val sessionProperties: Map<String, String>? = null,

    @Transient
    val activeEventIds: List<String>? = null,

    @SerializedName("et")
    @JvmField
    val logExceptionType: String? = null,

    @SerializedName("en")
    val exceptionName: String? = null,

    @SerializedName("em")
    val exceptionMessage: String? = null,

    @SerializedName("f")
    @JvmField
    val framework: Int? = null,
)
