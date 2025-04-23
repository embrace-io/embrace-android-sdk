package io.embrace.android.embracesdk.internal.instrumentation.bytecode

import android.view.View
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb.TapBreadcrumbType

private const val UNKNOWN_ELEMENT_NAME = "Unknown element"

internal fun logTouchEvent(view: View, breadcrumbType: TapBreadcrumbType) {
    try {
        val viewName = try {
            view.resources.getResourceName(view.id)
        } catch (e: Exception) {
            UNKNOWN_ELEMENT_NAME
        }
        val point: Pair<Float?, Float?> = try {
            Pair(view.x, view.y)
        } catch (e: Exception) {
            Pair(0.0f, 0.0f)
        }
        EmbraceInternalApi.getInstance().internalInterface.logTap(
            point,
            viewName,
            breadcrumbType
        )
    } catch (throwable: Throwable) {
        EmbraceInternalApi.getInstance().internalInterface.logInternalError(throwable)
    }
}
