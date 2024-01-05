package androidx.lifecycle

import android.app.Activity
import android.content.Context

/**
 * Used to access Package-Private methods in ProcessLifecycleOwner
 */
public object ProcessLifecycleOwnerAccess {

    public fun attach(context: Context) {
        ProcessLifecycleOwner.init(context)
    }

    public fun get(activity: Activity): ReportFragment {
        return ReportFragment.get(activity)
    }
}
