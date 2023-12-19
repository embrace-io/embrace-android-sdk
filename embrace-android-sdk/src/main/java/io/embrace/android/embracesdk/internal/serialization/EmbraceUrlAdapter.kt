package io.embrace.android.embracesdk.internal.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson
import io.embrace.android.embracesdk.comms.api.EmbraceUrl

@JsonClass(generateAdapter = true)
internal data class EmbraceUrlJson(
    @Json(name = "url")
    val url: String? = null
)

internal class EmbraceUrlAdapter {

    @FromJson
    fun fromJson(json: EmbraceUrlJson): EmbraceUrl? {
        val url = json.url ?: return null
        return EmbraceUrl.create(url)
    }

    @ToJson
    fun toJson(embraceUrl: EmbraceUrl?): EmbraceUrlJson? {
        val url = embraceUrl?.toString() ?: return null
        return EmbraceUrlJson(url)
    }
}
