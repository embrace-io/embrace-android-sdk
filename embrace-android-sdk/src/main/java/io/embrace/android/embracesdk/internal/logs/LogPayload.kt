package io.embrace.android.embracesdk.internal.logs

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class LogPayload(

    @Json(name = "logs")
    val logs: List<EmbraceLogRecordData> = emptyList()
)
