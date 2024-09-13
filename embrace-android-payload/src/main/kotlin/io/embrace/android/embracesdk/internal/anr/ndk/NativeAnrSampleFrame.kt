package io.embrace.android.embracesdk.internal.anr.ndk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class NativeAnrSampleFrame(
    @Json(name = "program_counter") val programCounter: String? = null,

    @Json(name = "so_load_addr") val soLoadAddr: String? = null,

    @Json(name = "so_name") val soName: String? = null,

    @Json(name = "result") val result: Int? = null
)
