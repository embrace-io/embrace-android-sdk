package io.embrace.android.embracesdk;

import io.embrace.android.embracesdk.session.ActivityListener;

/**
 * Provides hooks into the activity service that aren't accessible via Kotlin.
 */
public class ActivityServiceHooks {

    static void addListener(ActivityListener listener) {
        Embrace.getImpl().getActivityService().addListener(listener);
    }
}
