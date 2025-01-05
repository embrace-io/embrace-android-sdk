package io.embrace.android.embracesdk.fakes

import android.app.Activity
import android.os.Bundle
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener

class FakeActivityLifecycleListener : ActivityLifecycleListener {
    var onCreateInvokedCount = 0

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        onCreateInvokedCount++
    }
}
