package io.embrace.android.embracesdk.internal.anr.ndk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class NativeAnrSampleFrame(
    @Json(name = "program_counter")
    public val programCounter: String? = null,

    @Json(name = "so_load_addr")
    public val soLoadAddr: String? = null,

    @Json(name = "so_name")
    public val soName: String? = null,

    @Json(name = "result")
    public val result: Int? = null
)
