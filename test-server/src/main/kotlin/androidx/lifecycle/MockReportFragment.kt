package androidx.lifecycle

/**
 * Used to Mock the ReportFragment as part of mocking the activity lifecycle callbacks
 */
public class MockReportFragment : ReportFragment() {

    internal var embraceProcessListener: ActivityInitializationListener? = null

    internal override fun setProcessListener(processListener: ActivityInitializationListener?) {
        this.embraceProcessListener = processListener
    }

    public fun onLifecycleEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> embraceProcessListener?.onCreate()
            Lifecycle.Event.ON_START -> embraceProcessListener?.onStart()
            Lifecycle.Event.ON_RESUME -> embraceProcessListener?.onResume()
            else -> {} // do nothing
        }
    }
}
