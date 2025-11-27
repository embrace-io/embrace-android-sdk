package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.view.View
import androidx.annotation.Keep
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.TapBreadcrumbType
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.tapDataSource

/**
 * @hide
 */
@Keep
object OnLongClickBytecodeEntrypoint {

    @Suppress("unused")
    @Keep
    @JvmStatic
    fun onLongClick(view: View) {
        tapDataSource?.logTouchEvent(view, TapBreadcrumbType.LONG_PRESS)
    }
}
