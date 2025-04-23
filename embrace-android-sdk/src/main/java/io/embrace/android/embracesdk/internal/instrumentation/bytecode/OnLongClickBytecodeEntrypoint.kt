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

    @Suppress("UNUSED_PARAMETER")
    @Keep
    @JvmStatic
    public fun onLongClick(thiz: View.OnLongClickListener?, view: View) {
        logTouchEvent(view, TapBreadcrumbType.LONG_PRESS)
    }
}
