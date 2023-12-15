package io.embrace.android.embracesdk.payload

import androidx.annotation.VisibleForTesting
import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.Embrace.AppFramework.FLUTTER
import io.embrace.android.embracesdk.Embrace.AppFramework.NATIVE
import io.embrace.android.embracesdk.Embrace.AppFramework.REACT_NATIVE
import io.embrace.android.embracesdk.Embrace.AppFramework.UNITY

internal class Stacktraces @JvmOverloads constructor(
    stacktraces: List<String>? = null,
    customStacktrace: String? = null,
    framework: AppFramework = NATIVE,

    @SerializedName("c")
    @get:VisibleForTesting
    val context: String? = null,

    @SerializedName("l")
    @get:VisibleForTesting
    val library: String? = null
) {

    @SerializedName("tt")
    val jvmStacktrace: List<String>?

    @SerializedName("jsk")
    val javascriptStacktrace: String?

    @SerializedName("u")
    val unityStacktrace: String?

    @SerializedName("f")
    val flutterStacktrace: String?

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
