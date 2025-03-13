package io.embrace.android.embracesdk.internal.payload

/**
 * The frameworks in use by the app. Previous name: a.f
 *
 * Values: NATIVE,REACT_NATIVE,UNITY,FLUTTER
 */
enum class AppFramework(val value: Int) {
    NATIVE(1),
    REACT_NATIVE(2),
    UNITY(3),
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
