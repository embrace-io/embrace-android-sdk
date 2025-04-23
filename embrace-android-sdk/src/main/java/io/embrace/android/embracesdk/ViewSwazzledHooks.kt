package io.embrace.android.embracesdk

import android.view.View
import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb.TapBreadcrumbType

/**
 * @hide
 */
@InternalApi
public object ViewSwazzledHooks {
    private const val UNKNOWN_ELEMENT_NAME = "Unknown element"

    private fun logOnClickEvent(view: View, breadcrumbType: TapBreadcrumbType) {
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
        } catch (error: NoSuchMethodError) {
            // The customer may be overwriting View with their own implementation, and some of the
            // methods we use are missing.
            logError(error)
        } catch (exception: Exception) {
            logError(exception)
        }
    }

    private fun logError(throwable: Throwable) {
        EmbraceInternalApi.getInstance().internalInterface.logInternalError(throwable)
    }

    @InternalApi
    public object OnClickListener {
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic
        public fun _preOnClick(thiz: View.OnClickListener?, view: View) {
            logOnClickEvent(view, TapBreadcrumbType.TAP)
        }
    }

    @InternalApi
    public object OnLongClickListener {
        @JvmStatic
        public fun _preOnLongClick(thiz: View.OnLongClickListener?, view: View) {
            if (thiz != null) {
                logOnClickEvent(view, TapBreadcrumbType.LONG_PRESS)
            }
        }
    }
}
