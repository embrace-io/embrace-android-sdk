package io.embrace.android.embracesdk;

import androidx.annotation.NonNull;

import io.embrace.android.embracesdk.annotation.InternalApi;
import io.embrace.android.embracesdk.internal.EmbraceInternalApi;

/**
 * @hide
 */
@InternalApi
public final class AutoStartInstrumentationHook {

    private AutoStartInstrumentationHook() {
    }

    public static void _preOnCreate(android.app.Application application) {
        try {
            Embrace.getInstance().start(application);
        } catch (Exception exception) {
            logError(exception);
        }
    }

    private static void logError(@NonNull Throwable throwable) {
        EmbraceInternalApi.getInstance().getInternalInterface().logInternalError(throwable);
    }
}
