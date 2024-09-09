package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class ApplicationState {

    /**
     * Signals to the API that the application was in the foreground.
     */
    FOREGROUND,

    /**
     * Signals to the API that this is a background session.
     */
    BACKGROUND
}
