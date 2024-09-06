package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class Stacktraces @JvmOverloads constructor(
    stacktraces: List<String>? = null,
    customStacktrace: String? = null,
    framework: AppFramework = AppFramework.NATIVE,

    @Json(name = "c") val context: String? = null,

    @Json(name = "l") val library: String? = null
) {

    @Json(name = "tt")
    var jvmStacktrace: List<String>?

    @Json(name = "jsk")
    var javascriptStacktrace: String?

    @Json(name = "u")
    var unityStacktrace: String?

    @Json(name = "f")
    var flutterStacktrace: String?

    init {
        javascriptStacktrace = when (framework) {
            AppFramework.REACT_NATIVE -> customStacktrace
            else -> null
        }
        unityStacktrace = when (framework) {
            AppFramework.UNITY -> customStacktrace
            else -> null
        }
        flutterStacktrace = when (framework) {
            AppFramework.FLUTTER -> customStacktrace
            else -> null
        }

        this.jvmStacktrace = when (customStacktrace) {
            null -> stacktraces
            else -> null
        }
    }
}
