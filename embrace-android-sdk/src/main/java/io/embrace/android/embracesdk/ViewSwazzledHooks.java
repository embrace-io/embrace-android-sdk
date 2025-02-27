package io.embrace.android.embracesdk;

import androidx.annotation.NonNull;

import io.embrace.android.embracesdk.annotation.InternalApi;
import io.embrace.android.embracesdk.internal.EmbraceInternalApi;
import io.embrace.android.embracesdk.internal.payload.TapBreadcrumb.TapBreadcrumbType;
import kotlin.Pair;

/**
 * @hide
 */
@InternalApi
public final class ViewSwazzledHooks {

    private static final String UNKNOWN_ELEMENT_NAME = "Unknown element";

    private ViewSwazzledHooks() {
    }

    static void logOnClickEvent(android.view.View view, TapBreadcrumbType breadcrumbType) {
        try {
            String viewName = "";
            try {
                viewName = view.getResources().getResourceName(view.getId());
            } catch (Exception e) {
                viewName = UNKNOWN_ELEMENT_NAME;
            }
            Pair<Float, Float> point = null;
            try {
                point = new Pair<>(view.getX(), view.getY());
            } catch (Exception e) {
                point = new Pair<>(0.0F, 0.0F);
            }
            EmbraceInternalApi.getInstance().getInternalInterface().logTap(point, viewName, breadcrumbType);
        } catch (NoSuchMethodError error) {
            // The customer may be overwriting View with their own implementation, and some of the
            // methods we use are missing.
            logError(error);
        } catch (Exception exception) {
            logError(exception);
        }
    }

    private static void logError(@NonNull Throwable throwable) {
        EmbraceInternalApi.getInstance().getInternalInterface().logInternalError(throwable);
    }

    @InternalApi
    public static final class OnClickListener {
        private OnClickListener() {
        }

        @SuppressWarnings("MethodNameCheck")
        public static void _preOnClick(android.view.View.OnClickListener thiz, android.view.View view) {
            logOnClickEvent(view, TapBreadcrumbType.TAP);
        }
    }

    @InternalApi
    public static final class OnLongClickListener {
        private OnLongClickListener() {
        }

        @SuppressWarnings("MethodNameCheck")
        public static void _preOnLongClick(android.view.View.OnLongClickListener thiz, android.view.View view) {
            if (thiz != null) {
                logOnClickEvent(view, TapBreadcrumbType.LONG_PRESS);
            }
        }
    }
}
