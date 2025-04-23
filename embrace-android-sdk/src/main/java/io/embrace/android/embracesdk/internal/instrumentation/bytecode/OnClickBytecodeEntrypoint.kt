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
public object OnClickBytecodeEntrypoint {

    @Suppress("UNUSED_PARAMETER")
    @Keep
    @JvmStatic
    public fun onClick(thiz: View.OnClickListener?, view: View) {
        logTouchEvent(view, TapBreadcrumbType.TAP)
    }
}
