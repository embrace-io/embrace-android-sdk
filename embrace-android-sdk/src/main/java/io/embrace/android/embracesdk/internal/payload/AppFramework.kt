package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * The frameworks in use by the app. Previous name: a.f
 *
 * Values: NATIVE,REACT_NATIVE,UNITY,FLUTTER
 */
@JsonClass(generateAdapter = false)
@InternalApi
public enum class AppFramework(internal val value: Int) {
    @Json(name = "1")
    NATIVE(1),

    @Json(name = "2")
    REACT_NATIVE(2),

    @Json(name = "3")
    UNITY(3),

    @Json(name = "4")
    FLUTTER(4);

    internal companion object {

        fun fromInt(type: Int) = values().associateBy(AppFramework::value)[type]

        fun fromString(type: String?): AppFramework? = when (type) {
            "react_native" -> REACT_NATIVE
            "unity" -> UNITY
            "flutter" -> FLUTTER
            "native" -> NATIVE
            else -> null
        }

        @Suppress("DEPRECATION")
        fun fromFramework(appFramework: Embrace.AppFramework): AppFramework = when (appFramework) {
            Embrace.AppFramework.NATIVE -> NATIVE
            Embrace.AppFramework.REACT_NATIVE -> REACT_NATIVE
            Embrace.AppFramework.UNITY -> UNITY
            Embrace.AppFramework.FLUTTER -> FLUTTER
        }
    }
}
