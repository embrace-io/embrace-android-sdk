package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 *
 *
 * @param timestamp The timestamp in milliseconds of when an error happened. Previous name: s.e.rep.ts
 * @param appState The app state at the time of the error (foreground/background). Previous name: s.e.rep.s
 * @param exceptions A list of exceptions. Previous name: s.e.rep.ex
 */
@JsonClass(generateAdapter = true)
internal data class ExceptionErrorInfo(

    /* The timestamp in milliseconds of when an error happened. Previous name: s.e.rep.ts */
    @Json(name = "timestamp")
    val timestamp: Long? = null,

    /* The app state at the time of the error (foreground/background). Previous name: s.e.rep.s */
    @Json(name = "app_state")
    val appState: AppState? = null,

    /* A list of exceptions. Previous name: s.e.rep.ex */
    @Json(name = "exceptions")
    val exceptions: List<ExceptionInfo>? = null

) {

    /**
     * The app state at the time of the error (foreground/background). Previous name: s.e.rep.s
     *
     * Values: ACTIVE,BACKGROUND
     */
    internal enum class AppState(val value: String) {
        @Json(name = "active")
        ACTIVE("active"),

        @Json(name = "background")
        BACKGROUND("background")
    }
}
