package io.embrace.android.embracesdk;

import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener;

/**
 * Provides hooks into the activity service that aren't accessible via Kotlin.
 */
public class ActivityServiceHooks {

    static void addListener(ProcessStateListener listener) {
        Embrace.getImpl().getActivityService().addListener(listener);
    }
}
