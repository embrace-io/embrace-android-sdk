package io.embrace.android.embracesdk.internal.anr.detection;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Looper;
import android.os.MessageQueue;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;

class LooperCompat {

    /**
     * Retrieves the MessageQueue from a {@link Looper} via reflection. This is only required for
     * API <23 as {@link Looper#getQueue()} is available on newer APIs.
     * <p>
     * An alternative strategy would be {@link Looper#myQueue()} but that requires submitting to
     * the main thread's handler - not an option if it might be already blocked at the point
     * our SDK is initialized.
     * <p>
     * This hidden API shows up in the Android SDK's restricted interfaces, but as we don't
     * execute the code on newer API levels it should stay a false positive if a customer does
     * happen to flag this up due to automated scanners etc.
     *
     * <a href="https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces#test-for-non-sdk">...</a>
     */
    @Nullable
    @SuppressWarnings("JavaReflectionMemberAccess")
    @SuppressLint("PrivateApi")
    static MessageQueue getMessageQueue(Looper looper) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return looper.getQueue();
        } else {
            Class<Looper> clz = Looper.class;

            try {
                Field field = clz.getDeclaredField("mQueue");
                Object o = field.get(looper);
                return (MessageQueue) o;
            } catch (Throwable exc) {
                return null;
            }
        }
    }
}
