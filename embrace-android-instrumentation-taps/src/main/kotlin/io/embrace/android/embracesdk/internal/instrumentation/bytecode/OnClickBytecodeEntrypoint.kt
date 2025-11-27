package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.view.View
import androidx.annotation.Keep
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.TapBreadcrumbType
import io.embrace.android.embracesdk.internal.instrumentation.view.taps.tapDataSource

/**
 * @hide
 */
@Keep
object OnClickBytecodeEntrypoint {

    @Suppress("unused")
    @Keep
    @JvmStatic
    fun onClick(view: View) {
        tapDataSource?.logTouchEvent(view, TapBreadcrumbType.TAP)
    }
}
