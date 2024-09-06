package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
public enum class Unwinder(public val code: Int) {
    LIBUNWIND(0),
    LIBUNWINDSTACK(1)
}
