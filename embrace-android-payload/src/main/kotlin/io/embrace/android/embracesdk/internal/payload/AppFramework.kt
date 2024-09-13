package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The frameworks in use by the app. Previous name: a.f
 *
 * Values: NATIVE,REACT_NATIVE,UNITY,FLUTTER
 */
@JsonClass(generateAdapter = false)
enum class AppFramework(val value: Int) {
    @Json(name = "1")
    NATIVE(1),

    @Json(name = "2")
    REACT_NATIVE(2),

    @Json(name = "3")
    UNITY(3),

    @Json(name = "4")
    FLUTTER(4);

    companion object {

        fun fromInt(type: Int): AppFramework? = values().associateBy(AppFramework::value)[type]

        fun fromString(type: String?): AppFramework? = when (type) {
            "react_native" -> REACT_NATIVE
            "unity" -> UNITY
            "flutter" -> FLUTTER
            "native" -> NATIVE
            else -> null
        }
    }
}
