package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.view.View
import androidx.annotation.Keep
import io.embrace.android.embracesdk.internal.instrumentation.TapBreadcrumbType
import io.embrace.android.embracesdk.internal.instrumentation.tapDataSource

/**
 * @hide
 */
@Keep
object OnLongClickBytecodeEntrypoint {

    @Keep
    @JvmStatic
    fun onLongClick(view: View) {
        tapDataSource?.logTouchEvent(view, TapBreadcrumbType.LONG_PRESS)
    }
}
