package io.embrace.android.embracesdk.instrumentation.leaks

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

internal object WebViewLeakScanner {

    /**
     * Reported as [LeakContext.objectType].
     */
    const val WEBVIEW_OBJECT_TYPE = "webview"

    /**
     * Defensive bound against a pathologically deep/degenerate layout - not expected to ever be hit by a real
     * screen.
     */
    private const val MAX_DEPTH = 40

    fun scan(root: View?, errorHandler: ErrorHandler, onFound: (WebView) -> Unit) {
        root ?: return
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            var currentLevel: List<View> = listOf(root)
            var depth = 0
            while (currentLevel.isNotEmpty()) {
                val nextLevel = if (depth < MAX_DEPTH) ArrayList<View>() else null
                for (view in currentLevel) {
                    if (view is WebView) {
                        onFound(view)
                    } else if (view is ViewGroup && nextLevel != null) {
                        for (i in 0 until view.childCount) {
                            nextLevel.add(view.getChildAt(i))
                        }
                    }
                }
                currentLevel = nextLevel ?: emptyList()
                depth++
            }
        }
    }
}
