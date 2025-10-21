package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.view.View
import androidx.annotation.Keep
import io.embrace.android.embracesdk.internal.arch.retrieveDataSource
import io.embrace.android.embracesdk.internal.instrumentation.TapBreadcrumbType
import io.embrace.android.embracesdk.internal.instrumentation.TapDataSource

/**
 * @hide
 */
@Keep
object OnClickBytecodeEntrypoint {

    @Keep
    @JvmStatic
    fun onClick(view: View) {
        val dataSource = retrieveDataSource<TapDataSource>(TapDataSource.KEY) ?: return
        dataSource.logTouchEvent(view, TapBreadcrumbType.TAP)
    }
}
