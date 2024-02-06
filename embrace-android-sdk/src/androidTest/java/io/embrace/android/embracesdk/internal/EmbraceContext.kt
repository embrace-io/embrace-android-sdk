package io.embrace.android.embracesdk.internal

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MockReportFragment
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwnerAccess
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.FakePackageManager
import io.embrace.android.embracesdk.internal.MockActivity
import io.embrace.android.embracesdk.internal.PauseProcessListener
import io.embrace.android.embracesdk.utils.BitmapFactory
import io.embrace.android.embracesdk.utils.FailureLatch
import org.junit.Assert.assertEquals

/**
 * The super mocked Context. This is a very useful class for injecting Resources, including those
 * that BuildInfo and LocalConfig are read from, as well as mocking Activity Lifecycle Events
 */
public class EmbraceContext(public val context: Context) : Application() {
    public val activity: MockActivity
    public val handler: Handler = Handler(Looper.getMainLooper())

    private var currentState: Lifecycle.Event? = null
    private var pauseProcessListener: PauseProcessListener

    private val activityLifecycleCallbacks = mutableListOf<ActivityLifecycleCallbacks>()
    private val componentCallbacks = mutableListOf<ComponentCallbacks>()

    public var appId: String? = null
    public var sdkConfig: String? = null
    public var screenshotBitmap: Bitmap = BitmapFactory.newRandomBitmap(100, 100)

    init {
        attachBaseContext(context)
        pauseProcessListener = PauseProcessListener()
        ProcessLifecycleOwner.get().lifecycle.addObserver(pauseProcessListener)
        activity = MockActivity(this)
    }

    override fun onCreate() {
        super.onCreate()
    }

    public fun triggerOnLowMemory() {
        componentCallbacks.forEach { it.onLowMemory() }
    }

    public fun triggerActivityLifecycleEvent(state: Lifecycle.Event) {
        val bundle = Bundle()
        if (activity.baseContext == null) {
            activity.setContext(context)
        }

        val factor = when {
            Debug.isDebuggerConnected() -> 10L
            else -> 1L
        }
        val latch = FailureLatch(2000 * factor)
        val pauseLatch = FailureLatch(3000 * factor)

        // invoke Activity callbacks on the Main Thread, like it would in a real application.
        //
        // there is some funky behavior in the ProcessObserver where it has a fixed delay calling
        // the onPause callbacks. blocking using the pauseLatch until the callback is actually invoked
        // is the only way we can run this without arbitrary Thread.Sleep() calls
        handler.post {
            invokeCallback(state, bundle, pauseLatch, latch)
        }
        latch.await()

        if (state == Lifecycle.Event.ON_PAUSE) {
            pauseLatch.await()
        }

        currentState = state
    }

    private fun invokeCallback(
        state: Lifecycle.Event,
        bundle: Bundle,
        pauseLatch: FailureLatch,
        latch: FailureLatch
    ) {
        when (state) {
            Lifecycle.Event.ON_CREATE -> activityLifecycleCallbacks.forEach {
                it.onActivityCreated(activity, bundle)
            }

            Lifecycle.Event.ON_START -> activityLifecycleCallbacks.forEach {
                it.onActivityStarted(activity)
            }

            Lifecycle.Event.ON_RESUME -> activityLifecycleCallbacks.forEach {
                it.onActivityResumed(activity)
            }

            Lifecycle.Event.ON_PAUSE -> {
                pauseProcessListener.onPauseCallback = { pauseLatch.countDown() }
                activityLifecycleCallbacks.forEach {
                    it.onActivityPaused(activity)
                }
            }

            Lifecycle.Event.ON_STOP -> activityLifecycleCallbacks.forEach {
                it.onActivityStopped(activity)
            }

            Lifecycle.Event.ON_DESTROY -> activityLifecycleCallbacks.forEach {
                it.onActivityDestroyed(activity)
            }

            Lifecycle.Event.ON_ANY ->
                throw IllegalArgumentException("ON_ANY must not been send by anybody") // copy from the Android SDK code
        }
        (ProcessLifecycleOwnerAccess.get(activity) as MockReportFragment).onLifecycleEvent(state)
        latch.countDown()
    }

    public fun sendForeground() {
        currentState
        when (currentState) {
            null, Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_DESTROY -> {
                while (currentState != Lifecycle.Event.ON_START) {
                    currentState.nextEvent()?.let { triggerActivityLifecycleEvent(it) }
                }
            }

            Lifecycle.Event.ON_STOP -> {
                triggerActivityLifecycleEvent(Lifecycle.Event.ON_START)
            }

            else -> error("Cannot send App to foreground when current state is: $currentState")
        }
        assertEquals(
            "Failed to send application to Foreground",
            Lifecycle.Event.ON_START,
            currentState
        )
    }

    override fun getPackageManager(): PackageManager {
        return FakePackageManager(this)
    }

    public fun sendBackground() {
        var currentState = currentState
        when (currentState) {
            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_PAUSE -> {
                while (currentState != Lifecycle.Event.ON_STOP) {
                    currentState = currentState.nextEvent()
                    if (currentState != null) {
                        triggerActivityLifecycleEvent(currentState)
                    }
                }
            }

            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                error("Application is already in the background, current state = $currentState")
            }

            null, Lifecycle.Event.ON_CREATE -> {
                error("Application has not entered foreground, call sendForeground() first")
            }

            else -> {}
        }
        assertEquals(
            "Failed to send application to Background",
            currentState,
            Lifecycle.Event.ON_STOP
        )
    }

    override fun getResources(): Resources {
        return EmbraceResources(super.getResources(), appId, sdkConfig)
    }

    override fun getApplicationContext(): Context {
        return this
    }

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks?) {
        callback?.let { activityLifecycleCallbacks.add(it) }
    }

    override fun registerComponentCallbacks(callback: ComponentCallbacks) {
        componentCallbacks.add(callback)
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks?) {
        activityLifecycleCallbacks.remove(callback)
        super.unregisterActivityLifecycleCallbacks(callback)
    }

    public fun Lifecycle.Event?.nextEvent(): Lifecycle.Event? {
        return when (this) {
            null -> Lifecycle.Event.ON_CREATE
            Lifecycle.Event.ON_CREATE -> Lifecycle.Event.ON_START
            Lifecycle.Event.ON_START -> Lifecycle.Event.ON_RESUME
            Lifecycle.Event.ON_RESUME -> Lifecycle.Event.ON_PAUSE
            Lifecycle.Event.ON_PAUSE -> Lifecycle.Event.ON_STOP
            Lifecycle.Event.ON_STOP -> Lifecycle.Event.ON_DESTROY
            Lifecycle.Event.ON_DESTROY -> Lifecycle.Event.ON_CREATE
            Lifecycle.Event.ON_ANY -> null
        }
    }
}

@Suppress("DEPRECATION")
public class EmbraceResources(
    resources: Resources,
    appId: String?,
    sdkConfig: String?
) : Resources(
    resources.assets,
    resources.displayMetrics,
    resources.configuration
) {

    private val buildInfo = BuildInfo(
        buildId = "default test build id",
        buildType = "default test build type",
        buildFlavor = "default test build flavor"
    )

    private val resources = mapOf(
        BuildInfo.BUILD_INFO_BUILD_ID.hashCode() to buildInfo.buildId,
        BuildInfo.BUILD_INFO_BUILD_FLAVOR.hashCode() to buildInfo.buildFlavor,
        BuildInfo.BUILD_INFO_BUILD_TYPE.hashCode() to buildInfo.buildType,
        "emb_app_id".hashCode() to appId,
        "emb_sdk_config".hashCode() to sdkConfig
    )

    @SuppressWarnings("DiscouragedApi")
    override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
        return name.hashCode()
    }

    override fun getString(id: Int): String {
        return resources[id] ?: ""
    }
}
