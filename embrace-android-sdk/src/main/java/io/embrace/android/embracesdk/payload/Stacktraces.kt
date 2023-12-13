package io.embrace.android.embracesdk.payload

import androidx.annotation.VisibleForTesting
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.Embrace.AppFramework.FLUTTER
import io.embrace.android.embracesdk.Embrace.AppFramework.NATIVE
import io.embrace.android.embracesdk.Embrace.AppFramework.REACT_NATIVE
import io.embrace.android.embracesdk.Embrace.AppFramework.UNITY

@JsonClass(generateAdapter = true)
internal class Stacktraces @JvmOverloads constructor(
    stacktraces: List<String>? = null,
    customStacktrace: String? = null,
    framework: AppFramework = NATIVE,

    @Json(name = "c")
    @get:VisibleForTesting
    val context: String? = null,

    @Json(name = "l")
    @get:VisibleForTesting
    val library: String? = null
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
            REACT_NATIVE -> customStacktrace
            else -> null
        }
        unityStacktrace = when (framework) {
            UNITY -> customStacktrace
            else -> null
        }
        flutterStacktrace = when (framework) {
            FLUTTER -> customStacktrace
            else -> null
        }

        this.jvmStacktrace = when (customStacktrace) {
            null -> stacktraces
            else -> null
        }
    }
}
