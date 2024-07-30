package io.embrace.android.embracesdk.internal.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class Stacktraces @JvmOverloads constructor(
    stacktraces: List<String>? = null,
    customStacktrace: String? = null,
    framework: AppFramework = AppFramework.NATIVE,

    @Json(name = "c")
    public val context: String? = null,

    @Json(name = "l")
    public val library: String? = null
) {

    @Json(name = "tt")
    public var jvmStacktrace: List<String>?

    @Json(name = "jsk")
    public var javascriptStacktrace: String?

    @Json(name = "u")
    public var unityStacktrace: String?

    @Json(name = "f")
    public var flutterStacktrace: String?

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
