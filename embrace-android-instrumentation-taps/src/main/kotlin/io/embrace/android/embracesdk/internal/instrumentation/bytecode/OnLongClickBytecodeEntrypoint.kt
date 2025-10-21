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
object OnLongClickBytecodeEntrypoint {

    @Keep
    @JvmStatic
    fun onLongClick(view: View) {
        val dataSource = retrieveDataSource<TapDataSource>(TapDataSource.KEY) ?: return
        dataSource.logTouchEvent(view, TapBreadcrumbType.LONG_PRESS)
    }
}
