package io.embrace.android.embracesdk.payload

import androidx.annotation.VisibleForTesting
import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.Embrace.AppFramework.FLUTTER
import io.embrace.android.embracesdk.Embrace.AppFramework.REACT_NATIVE
import io.embrace.android.embracesdk.Embrace.AppFramework.UNITY

internal class Stacktraces @JvmOverloads constructor(
    stacktraces: List<String>?,
    customStacktrace: String?,
    framework: AppFramework,

    @SerializedName("c")
    @get:VisibleForTesting
    val context: String? = null,

    @SerializedName("l")
    @get:VisibleForTesting
    val library: String? = null
) {

    @SerializedName("tt")
    @VisibleForTesting
    val jvmStacktrace: List<String>?

    @SerializedName("jsk")
    @VisibleForTesting
    val javascriptStacktrace: String?

    @SerializedName("u")
    @VisibleForTesting
    val unityStacktrace: String?

    @SerializedName("f")
    @VisibleForTesting
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
