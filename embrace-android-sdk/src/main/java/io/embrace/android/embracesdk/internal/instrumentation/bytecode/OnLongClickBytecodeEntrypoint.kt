package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.view.View
import androidx.annotation.Keep
import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb.TapBreadcrumbType

/**
 * @hide
 */
@InternalApi
@Keep
public object OnLongClickBytecodeEntrypoint {

    @Keep
    @JvmStatic
    public fun onLongClick(view: View) {
        logTouchEvent(view, TapBreadcrumbType.LONG_PRESS)
    }
}
